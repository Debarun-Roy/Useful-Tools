package backendsupport;

import com.google.gson.*;
import java.util.*;
import static backendsupport.SchemaGeneration.*;

/** Renderers only accept a successfully validated normalized model. */
public sealed interface SchemaDialect permits SchemaDialect.SQLite,SchemaDialect.PostgreSQL {
    String type(JsonObject col);
    String timestamp();
    String preamble();
    default String quote(String name){return "\""+name.replace("\"","\"\"")+"\"";}
    default String literal(JsonElement value) {
        if(value.isJsonNull())return "NULL";
        JsonPrimitive p=value.getAsJsonPrimitive();
        if(p.isBoolean())return p.getAsBoolean()?"TRUE":"FALSE";
        if(p.isNumber())return p.getAsBigDecimal().stripTrailingZeros().toPlainString();
        return "'"+p.getAsString().replace("'","''")+"'";
    }
    default String columns(JsonObject table,JsonArray ids){return String.join(", ",SchemaGeneration.ids(ids).stream().map(id->quote(str(column(table,id),"name"))).toList());}
    default String render(Model model) {
        ArtifactBundle.Text sql=new ArtifactBundle.Text();sql.add(preamble());
        for(JsonObject t:model.tables()) {
            List<String> definitions=new ArrayList<>();
            for(JsonElement e:t.getAsJsonArray("columns")) {
                JsonObject c=e.getAsJsonObject();String name=quote(str(c,"name"));String definition="  "+name+" "+type(c);
                if(!c.get("nullable").getAsBoolean())definition+=" NOT NULL";
                if(c.has("default")) {JsonObject d=c.getAsJsonObject("default");definition+=" DEFAULT "+(str(d,"kind").equals("currentTimestamp")?timestamp():literal(d.get("value")));}
                if(this instanceof SQLite) {
                    switch(str(c,"type")) {
                        case "varchar" -> definition+=" CHECK (length("+name+") <= "+c.get("length").getAsInt()+")";
                        case "boolean" -> definition+=" CHECK ("+name+" IN (0, 1))";
                        case "json" -> definition+=" CHECK ("+name+" IS NULL OR json_valid("+name+"))";
                        default -> { }
                    }
                }
                definitions.add(definition);
            }
            definitions.add("  CONSTRAINT "+quote("ut_pk_"+str(t,"id"))+" PRIMARY KEY ("+columns(t,t.getAsJsonArray("primaryKey"))+")");
            int u=0;for(JsonElement e:t.getAsJsonArray("unique"))definitions.add("  CONSTRAINT "+quote("ut_uq_"+str(t,"id")+"_"+u++)+" UNIQUE ("+columns(t,e.getAsJsonArray())+")");
            for(JsonElement e:t.getAsJsonArray("foreignKeys")) {
                JsonObject f=e.getAsJsonObject(),target=model.byId().get(str(f,"tableId"));
                definitions.add("  FOREIGN KEY ("+columns(t,f.getAsJsonArray("columns"))+") REFERENCES "+quote(str(target,"name"))+" ("+columns(target,f.getAsJsonArray("targetColumns"))+") ON DELETE "+str(f,"onDelete")+" ON UPDATE "+str(f,"onUpdate"));
            }
            for(JsonElement e:t.getAsJsonArray("checks")) {
                JsonObject c=e.getAsJsonObject();String op=switch(str(c,"op")){case "eq"->"=";case "ne"->"<>";case "lt"->"<";case "le"->"<=";case "gt"->">";case "ge"->">=";default->throw new IllegalStateException();};
                definitions.add("  CHECK ("+quote(str(column(t,str(c,"column")),"name"))+" "+op+" "+literal(c.get("value"))+")");
            }
            sql.add("CREATE TABLE "+quote(str(t,"name"))+" (\n"+String.join(",\n",definitions)+"\n);\n");
            for(JsonElement e:t.getAsJsonArray("indexes")) {
                JsonObject i=e.getAsJsonObject();sql.add("CREATE "+(i.get("unique").getAsBoolean()?"UNIQUE ":"")+"INDEX "+quote(str(i,"name"))+" ON "+quote(str(t,"name"))+" ("+columns(t,i.getAsJsonArray("columns"))+");\n");
            }
            sql.add("\n");
        }
        return sql.value();
    }
    final class SQLite implements SchemaDialect {
        public String type(JsonObject c){return switch(str(c,"type")){case "integer"->"INT";case "bigint"->"BIGINT";case "decimal"->"NUMERIC";case "boolean"->"INTEGER";default->"TEXT";};}
        public String timestamp(){return "(strftime('%Y-%m-%dT%H:%M:%fZ','now'))";}
        public String preamble(){return "-- Initial schema: enable and CONFIRM foreign_keys on this connection BEFORE any transaction.\nPRAGMA foreign_keys = ON;\n\n";}
    }
    final class PostgreSQL implements SchemaDialect {
        public String type(JsonObject c){return switch(str(c,"type")){case "decimal"->"NUMERIC("+c.get("precision").getAsInt()+","+c.get("scale").getAsInt()+")";case "varchar"->"VARCHAR("+c.get("length").getAsInt()+")";case "timestamp"->"TIMESTAMPTZ";case "json"->"JSONB";default->str(c,"type").toUpperCase(Locale.ROOT);};}
        public String timestamp(){return "CURRENT_TIMESTAMP";}
        public String preamble(){return "-- Initial schema in an empty, caller-owned database; names are quoted.\nSET standard_conforming_strings = on;\nSET TIME ZONE 'UTC';\n\n";}
    }
}
