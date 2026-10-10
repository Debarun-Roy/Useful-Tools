package backendsupport;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class B4Test {
    static JsonObject sample(){return Contracts.resource("contracts/v0.4.0/etl-sample.json");}
    static JsonObject mapping(JsonObject s,int i){return s.getAsJsonArray("mappings").get(i).getAsJsonObject();}
    static B3Generation.Result valid(JsonObject s){var r=EtlGeneration.process(s);assertTrue(r.validation().valid(),()->new Gson().toJson(r.validation().items));assertNotNull(r.bundle());return r;}
    static void invalid(JsonObject s,String code){var r=EtlGeneration.process(s);assertNull(r.bundle());assertTrue(r.validation().items.stream().anyMatch(f->f.ruleId().equals(code)),()->new Gson().toJson(r.validation().items));}
    static JsonObject upsert(){var s=sample();s.addProperty("mode","upsert");s.addProperty("resumePolicy","upsert_replay");s.add("conflictKey",JsonParser.parseString("[\"id\"]"));s.add("updateColumns",JsonParser.parseString("[\"name\",\"amount\"]"));return s;}
    @Test void fourCombinationsDeterministic(){for(String target:List.of("sqlite","postgresql"))for(String source:List.of("csv","json")){var s=sample();s.addProperty("target",target);s.getAsJsonObject("schema").addProperty("target",target);s.getAsJsonObject("source").addProperty("format",source);var r=valid(s);assertArrayEquals(r.bundle().zip(),valid(s).bundle().zip());assertEquals(9,r.bundle().files().size());assertTrue(r.bundle().files().get("etl.py").contains("SAVEPOINT ut_etl_row"));}}
    @Test void explicitUpsertAndReplayPolicy(){valid(upsert());var s=upsert();s.addProperty("resumePolicy","manual_insert");invalid(s,"ETL_RESUME_POLICY");s=upsert();s.add("conflictKey",JsonParser.parseString("[\"amount\"]"));invalid(s,"ETL_EXPLICIT_UNIQUE_KEY");s=upsert();mapping(s,0).addProperty("missing","default");invalid(s,"ETL_KEY_MUST_BE_EXPLICIT");s=upsert();s.add("updateColumns",JsonParser.parseString("[\"id\"]"));invalid(s,"ETL_UPDATE_COLUMN");}
    @Test void mappedReferencesAndRequiredColumns(){var s=sample();mapping(s,0).addProperty("columnId","absent");invalid(s,"UNKNOWN_ETL_COLUMN");s=sample();s.getAsJsonArray("mappings").remove(0);invalid(s,"ETL_REQUIRED_COLUMN_UNMAPPED");s=sample();mapping(s,1).addProperty("source","id");invalid(s,"DUPLICATE_SOURCE_FIELD");s=sample();mapping(s,1).addProperty("columnId","id");invalid(s,"DUPLICATE_ETL_COLUMN");}
    @Test void orderedTypedTransforms(){var s=sample();mapping(s,0).add("transforms",JsonParser.parseString("[\"cast\",\"trim\"]"));invalid(s,"ETL_FINAL_CONVERSION");s=sample();mapping(s,0).add("transforms",JsonParser.parseString("[\"lower\",\"cast\"]"));invalid(s,"ETL_TRANSFORM_ORDER");s=sample();mapping(s,4).add("transforms",JsonParser.parseString("[\"date_dmy\"]"));valid(s);}
    @Test void independentMissingNullEmptyPolicies(){var s=sample();mapping(s,0).addProperty("null","null");invalid(s,"ETL_NULL_POLICY");s=sample();mapping(s,0).addProperty("missing","default");invalid(s,"ETL_DEFAULT_REQUIRED");s=sample();mapping(s,0).addProperty("empty","keep");invalid(s,"ETL_EMPTY_KEEP_TEXT_ONLY");}
    @Test void targetAndRuntimeIdentity(){var s=sample();s.addProperty("target","postgresql");invalid(s,"DIALECT_MISMATCH");s=sample();s.getAsJsonObject("runtime").addProperty("connectionEnv","UT_ETL_SOURCE");invalid(s,"ETL_DISTINCT_ENV_NAMES");}
    @Test void userTextStaysData(){var s=sample();String attack="__import__('os').system('no')";mapping(s,1).addProperty("source",attack);var r=valid(s);assertFalse(r.bundle().files().get("etl.py").contains(attack));assertTrue(r.bundle().files().get("etl.spec.json").contains(attack));}
    @Test void structuralUnknownOptionsAndBounds(){for(String field:List.of("connectionString","pythonCode","url")){var s=sample();s.addProperty(field,"not accepted");assertFalse(EtlGeneration.process(s).validation().valid());}for(int size:List.of(0,1001)){var s=sample();s.addProperty("batchSize",size);assertFalse(EtlGeneration.process(s).validation().valid());}var s=sample();s.addProperty("schemaVersion","0.1.0");assertFalse(EtlGeneration.process(s).validation().valid());}
}
