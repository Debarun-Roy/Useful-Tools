package backendsupport;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Independently versioned Python source renderer; never executes generated code. */
public final class PythonAuthGeneration {
    public static final String VERSION="0.6.0", TEMPLATE="python-auth-0.6.0-b6";
    private static final JsonObject CONTRACT=Contracts.resource("contracts/v0.6.0/models.schema.json");
    private static final Set<String> RESERVED=Set.of(("abc and annotated_doc annotated_types annotationlib antigravity anyio argon2 argparse array as assert ast async asyncio atexit await base64 bdb binascii bisect break builtins bz2 calendar case certifi cffi class click cmath cmd code codecs codeop collections colorama colorsys compileall compression concurrent configparser contextlib contextvars continue copy copyreg csv ctypes curses dataclasses datetime dbm decimal def del difflib dis doctest elif else email encodings ensurepip enum errno except false fastapi faulthandler fcntl filecmp fileinput finally fnmatch for fractions from ftplib functools gc genericpath getopt getpass gettext glob global graphlib grp gzip h11 hashlib heapq hmac html http httpcore httpx idlelib idna if imaplib import importlib in iniconfig inspect io ipaddress is itertools itsdangerous json keyword lambda linecache locale logging lzma mailbox marshal match math mimetypes mmap modulefinder msvcrt multiprocessing netrc none nonlocal not nt ntpath nturl2path numbers opcode operator optparse or os packaging pass pathlib pdb pickle pickletools pkgutil platform plistlib pluggy poplib posix posixpath pprint profile pstats psycopg psycopg_binary pty pwd py_compile pyclbr pycparser pydantic pydantic_core pydoc pydoc_data pyexpat pygments pytest queue quopri raise random re readline redis reprlib resource return rlcompleter runpy sched secrets select selectors setup shelve shlex shutil signal site smtplib socket socketserver sqlite3 sre_compile sre_constants sre_parse ssl starlette starsessions stat statistics string stringprep struct subprocess symtable sys sysconfig syslog tabnanny tarfile tempfile termios test tests textwrap this threading time timeit tkinter token tokenize tomllib trace traceback tracemalloc true try tty turtle turtledemo type types typing typing_extensions typing_inspection tzdata unicodedata unittest urllib uuid uvicorn venv warnings wave weakref webbrowser while winreg winsound with wsgiref xml xmlrpc yield zipapp zipfile zipimport zlib zoneinfo").split(" "));
    private PythonAuthGeneration() {}
    public static B3Generation.Result process(JsonElement input) {
        Contracts.Findings findings=Contracts.structural(input,CONTRACT);
        if(!findings.valid())return new B3Generation.Result(findings,null,Map.of());
        JsonObject spec=input.getAsJsonObject().deepCopy();String module=spec.get("pythonModule").getAsString();
        if(RESERVED.contains(module)||module.startsWith("__"))findings.add("PYTHON_MODULE","/pythonModule");
        try {ArtifactBundle.safeProjectPath(module+"/__init__.py",new HashSet<>());ArtifactBundle.safeProjectPath(spec.get("project").getAsString()+".zip",new HashSet<>());}
        catch(IllegalArgumentException error){findings.add("PROJECT_PATH","/pythonModule");}
        Set<String> names=new HashSet<>();for(var entry:spec.getAsJsonObject("runtime").entrySet())if(!names.add(entry.getValue().getAsString()))findings.add("DISTINCT_ENV_NAMES","/runtime");
        if(!names.add(spec.getAsJsonObject("captcha").get("secretEnv").getAsString()))findings.add("DISTINCT_ENV_NAMES","/captcha/secretEnv");
        if(names.stream().anyMatch(n->Set.of("WEB_CONCURRENCY","PYTHONPATH","PYTHONHOME","PATH").contains(n)))findings.add("RESERVED_ENV_NAME","/runtime");
        // Reuse released common security policy validation through a deliberate inert adapter.
        // Neither this adapter nor its discarded Java bundle changes released Java bytes.
        JsonObject policy=spec.deepCopy();policy.remove("pythonModule");policy.remove("pythonVersion");policy.getAsJsonObject("runtime").remove("redisEnv");
        policy.addProperty("packageName","example.auth");policy.addProperty("container","tomcat-11");policy.addProperty("target","java");
        policy.addProperty("schemaVersion",AuthGeneration.VERSION);policy.addProperty("templateVersion",AuthGeneration.TEMPLATE);
        var common=AuthGeneration.process(policy);
        if(!common.validation().valid())return new B3Generation.Result(common.validation(),null,Map.of());
        if(!findings.valid())return new B3Generation.Result(findings,null,Map.of());
        ArtifactBundle out=ArtifactBundle.project();
        for(String name:List.of("__init__","models","wire","config","security","sessions","repository","captcha","app"))
            out.add(module+"/"+name+".py",resource(name+".py.txt"));
        out.add(module+"/__main__.py",resource("main.py.txt").replace("@MODULE@",module));
        out.add("tests/test_runtime.py",resource("test_runtime.py.txt").replace("@MODULE@",module));
        out.add("requirements.txt",resource("requirements.txt"));
        out.add("pyproject.toml","[project]\nname = \""+spec.get("project").getAsString()+"\"\nversion = \"0.6.0\"\nrequires-python = \"==3.14.*\"\n\n[tool.pytest.ini_options]\npythonpath = [\".\"]\ntestpaths = [\"tests\"]\n");
        for(String name:List.of("README.md","SECURITY.md"))out.add(name,resource(name).replace("@MODULE@",module));
        out.add(module+"/V001__auth.sql",resource("V001__auth.sql"));
        String config=ArtifactBundle.canonical(spec)+"\n",openapi=ArtifactBundle.canonical(AuthOpenApi.document(policy))+"\n";
        out.add("auth-spec.json",config);out.add(module+"/auth-spec.json",config);out.add("openapi.json",openapi);out.add(module+"/openapi.json",openapi);
        JsonObject env=new JsonObject();for(var entry:spec.getAsJsonObject("runtime").entrySet())env.addProperty(entry.getValue().getAsString(),entry.getKey().equals("initializeEnv")?"false":"REPLACE_WITH_OPERATOR_VALUE");
        if(spec.getAsJsonObject("captcha").get("mode").getAsString().equals("recaptcha-v3"))env.addProperty(spec.getAsJsonObject("captcha").get("secretEnv").getAsString(),"REPLACE_WITH_PROVIDER_SECRET");
        out.add("environment.example.json",ArtifactBundle.canonical(env)+"\n");
        JsonObject manifest=new JsonObject();manifest.addProperty("manifestVersion","1.3.0");manifest.addProperty("schemaVersion",VERSION);manifest.addProperty("templateVersion",TEMPLATE);
        manifest.addProperty("module","rest");manifest.addProperty("target","python");manifest.addProperty("database",spec.get("database").getAsString());manifest.addProperty("specificationDigest",ArtifactBundle.sha(config.strip()));
        return new B3Generation.Result(findings,out.finish(manifest),Map.of("operatorRun",true,"coreDependencies",List.of("user persistence","Argon2id","real Redis server-side sessions","Origin and CSRF","bounded throttling"),"workers",1));
    }
    private static String resource(String name) {
        try(InputStream in=PythonAuthGeneration.class.getResourceAsStream("/backendsupport/b6/"+name)) {
            if(in==null)throw new IllegalStateException("MISSING_PYTHON_TEMPLATE");return new String(in.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n");
        }catch(IOException error){throw new IllegalStateException("MISSING_PYTHON_TEMPLATE");}
    }
}
