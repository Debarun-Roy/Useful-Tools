package emotes;

import java.sql.Connection;

/** Explicit operator lifecycle entry point; no servlet/listener calls this class. */
public final class EmoteAdmin {
    private EmoteAdmin() { }
    public static void main(String[] args) {
        boolean install=args.length==1 && "migrate-import".equals(args[0]);
        boolean activate=args.length==2 && "activate-retained".equals(args[0]) && !args[1].isBlank();
        if(!install && !activate) throw new IllegalArgumentException("Expected migrate-import or activate-retained <version>");
        String path=System.getenv("SQLITE_DB_PATH"),url=System.getenv("SQLITE_DB_URL");
        if((path==null || path.isBlank()) && (url==null || url.isBlank()))
            throw new IllegalArgumentException("Explicit database configuration required; default storage is forbidden for maintenance");
        try(Connection c=EmoteDb.open()) {
            CatalogSeed.load(); // Fail missing/damaged package before any migration.
            if(install){EmoteMigration.migrate(c);CatalogImport.install(c);}
            else CatalogImport.activate(c,args[1]);
            System.out.println("Emote maintenance complete; tool state unchanged (default disabled)");
        }catch(Exception e){System.err.println("Emote maintenance failed; inspect sanitized prerequisite evidence");System.exit(1);}
    }
}
