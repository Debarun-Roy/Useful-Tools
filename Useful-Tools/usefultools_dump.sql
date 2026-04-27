PRAGMA foreign_keys=OFF;
BEGIN TRANSACTION;
CREATE TABLE IF NOT EXISTS "generator_table"(

	id INTEGER PRIMARY KEY AUTOINCREMENT,

	password VARCHAR(500) NOT NULL,

	number_count INTEGER,

	special_character_count INTEGER,

	lowercase_count INTEGER,

	uppercase_count INTEGER,

	generated_timestamp TIMESTAMP NOT NULL

, username TEXT NOT NULL DEFAULT '');
INSERT INTO generator_table VALUES(1,';ZI-7591KUWB*C0RNUGFSHA]0UO1',7,4,0,17,1775215263541,'Deba_exe');
INSERT INTO generator_table VALUES(2,'Q0Hx''r{29C3@FO;BQIIE-t&lR0VhOe{06u#2Xa~H',8,9,8,15,1775303521466,'Deba_exe');
CREATE TABLE IF NOT EXISTS "user_table"(

	username VARCHAR(500) NOT NULL,

	hashed_password VARCHAR(5000) NOT NULL

, failed_attempts INTEGER DEFAULT 0, locked_until    TEXT, created_date TEXT);
INSERT INTO user_table VALUES('Deba_exe','$2a$12$f2A3ZHvMIcRP1gOndj/lU.kttGTzWH2lJ20yJptIeQflGoYu4/x1C',0,NULL,'2026-04-03 19:14:58');
CREATE TABLE expr_table(

	expr varchar(50000) not null,

	ans varchar(50000) not null

);
INSERT INTO expr_table VALUES('4*3','12.0');
INSERT INTO expr_table VALUES('1|1','1.0');
INSERT INTO expr_table VALUES('43%21','1.0');
INSERT INTO expr_table VALUES('sin(23)','-0.8462204041751706');
INSERT INTO expr_table VALUES('sin(90)','0.8939966636005579');
INSERT INTO expr_table VALUES('sin(3.141592653589793/2)','1.0');
INSERT INTO expr_table VALUES('xor(1,1)','0.0');
INSERT INTO expr_table VALUES('max(1)','1.0');
INSERT INTO expr_table VALUES('max(4,2,42,43)','43.0');
INSERT INTO expr_table VALUES('xor(1,0)','1.0');
INSERT INTO expr_table VALUES('xor(1,1,1,1)','0.0');
INSERT INTO expr_table VALUES('xor(1,1,0,1)','0.0');
INSERT INTO expr_table VALUES('xor(1,1,1)','0.0');
INSERT INTO expr_table VALUES('xor(0,0,0)','0.0');
INSERT INTO expr_table VALUES('xor(1,0)','1.0');
INSERT INTO expr_table VALUES('xor(0,1,0)','1.0');
INSERT INTO expr_table VALUES('xor(0,0,0,1)','0.0');
INSERT INTO expr_table VALUES('1&xor(1,0)','1.0');
INSERT INTO expr_table VALUES('xor(1&1,0&0)','1.0');
INSERT INTO expr_table VALUES('parity(1,1,1)','1.0');
INSERT INTO expr_table VALUES('parity(1,1,1)','1.0');
INSERT INTO expr_table VALUES('majority(1,1,0)','1.0');
INSERT INTO expr_table VALUES('implication(1,1)','1.0');
INSERT INTO expr_table VALUES('reverseImplication(1,1,0)','1.0');
INSERT INTO expr_table VALUES('biconditional(1,1)','1.0');
INSERT INTO expr_table VALUES('nonimplication(1,1,0)','0.0');
INSERT INTO expr_table VALUES('converseNonimplication(1,1,0)','0.0');
INSERT INTO expr_table VALUES('5|4','1.0');
INSERT INTO expr_table VALUES('sinh(7)','548.3161232732465');
INSERT INTO expr_table VALUES('sinh(10)','11013.232874703393');
INSERT INTO expr_table VALUES('implication(1,1)','1.0');
INSERT INTO expr_table VALUES('converseNonimplication(1,0)','0.0');
INSERT INTO expr_table VALUES('nand(1,1,0,1)','0.0');
INSERT INTO expr_table VALUES('10+9','19.0');
INSERT INTO expr_table VALUES('92*(1+0.15)','105.8');
INSERT INTO expr_table VALUES('sin(30)+cos(30)','-0.8337801742052777');
INSERT INTO expr_table VALUES('sind(90)+cosd(0)','2.0');
INSERT INTO expr_table VALUES('complex_add(2+3i,1+2i)','3.0+5.0i');
INSERT INTO expr_table VALUES('complex_add(2+1i,3+2i)','5.0+3.0i');
INSERT INTO expr_table VALUES('complex_subtract(2+1i,3+2i)','-1.0-1.0i');
INSERT INTO expr_table VALUES('conj(2+1i)','2.0-1.0i');
INSERT INTO expr_table VALUES('real(2,1)','2.0');
INSERT INTO expr_table VALUES('imag(2,1)','1.0');
INSERT INTO expr_table VALUES('csq(2,1)','3.0');
INSERT INTO expr_table VALUES('sind(90)+tand(45)+cosd(0)','3.0');
INSERT INTO expr_table VALUES('max(9,8,4,1)','9.0');
INSERT INTO expr_table VALUES('sinhd(2)','0.034913674241017253');
INSERT INTO expr_table VALUES('tand(90)','1.633123935319537E16');
INSERT INTO expr_table VALUES('55555555^333333333','Infinity');
INSERT INTO expr_table VALUES('1/4','0.25');
CREATE TABLE encryption_table (

  username          TEXT NOT NULL,

  platform          TEXT NOT NULL,

  encrypted_password TEXT,

  private_key       TEXT,

  created_date      TEXT,

  PRIMARY KEY (username, platform)

);
INSERT INTO encryption_table VALUES('Deba_exe','github.com','GUtxgkz/vmdGJxpHmIwWUC3k60nZsq7dTIIH8OSqZYS1LZjzqb9JIu/Bsi8EGKZQgq79tcedFdWYSt4lnKRUhQTkcMb58Cx6hllM6oMlV+AreRwW0TQvQ3AE/7pU3CeYzROB42vveW+k1bYewAIifZyvwffJKoI8LxsSuy6xBADN9Iz60WvXHLvgj7S5cm9LGG7JQo2Lx2K9lPg0FSoLVveT073pL3SxRRnWGyNGKUuOJnegW+dCREVtoxaCKqivhdZQOx+VrxATifKO3tL0z6MRAeeMRxwXy8w+7+buUld0O9B5wxnI6mp7WkjQ9+VRBFXazK9BH3qSAlcITRFhJw==','MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCWptp6AFZDzYhuVI7g3ixQSXhHH1xj28UlRfN9FJWP9G6oavad0VxBvn3nAQfsh0+Q3n8Fr9O/SYh7QCTLGR+XFwz5TeLeDAuwAdmrwjj6Z3VtAHvFfZsCxc8iWJ3qQL3INsWS1fEYs4akjy13gyWxoqtLnqiahFy8iWwbmOuZqyES3cKbFg5TD4F1dZ6v+0bKXIY5Jd0zgFaTfFeVSiYLiVwID8RJROTqMHyJVyBeakguZT73pdMEhrQzpkPZR2UvTJYSYDSEsd9eGzqxXFtQ39x+O/k6ZRc2pNAgIqXF67/dONRp/kYLDP//33cj3IBFFnia75q2cL1TR7uzSUSlAgMBAAECggEAMhaQrh59j/Z+hVxE0tvbdOgwfskdATB5GB7tSSl2XpHg1ssVarxIha1Nk+fiWBBW8m4fYmcmqkNLBtt5QDN+rs/hpuPv6W6s1hyPYA1nv011m8Oc4zkTODVHXH6FJnTXBIG9Z8gM7G8H9Z+K+ODYP/q7JB3ApfsbEC5KjNRnhMP/lEPBcXLZK2tvO/pPsV1x47CPcO7i+qKs857deU9zmDlPWcvCCQYgkmnZKuCYYzeZDN1rJx0zuU4woMq8LDMukiLZzvlOyz4wmvuU2uDES3S23ixyNCLDza9Fd9CrqLnvNzajCH65UTBF2k6YNyMhIWwxnk0RkC7202C10EbMwwKBgQDRYlH8FeFm1kg16VRjGXBkNjt5TVvUFg0BT61IHTUB9rCtrTAlZCAH3XK8THX4sudP59KbeaDwdH4RfgMj80lrkkqYWsVczZEnZm7GtgagwMQ59fu0GrSxAZdKM/coOLyI2MeVXETR+CrEW030E7X3i4PSONAJZ7KoKSuhqWF0mwKBgQC4MSHnoED6MhcdT6QEphEhJqqUg4bVaI2CIUaD2DNBEcl3LPO/ki2XeRhizBCNYyCnJRnU5T7ce4h7Dr9W9zeujvIGb9YJJFC1UmzFTSnnXsHRSy7fG7HhW8gjkQ51olBCT+ONmrlwIJBp/CZ5dixAqcRVxOShpy5dmnsEkWWfvwKBgB3u2VuZYSzLa21Rmv/Dzj51RlFUDtGmCVvroRETI9XRB3b8sEpYIwAc11816xOPt+PDd2dEnw5hCPjFM/u6tv9K3aEkMNzQgnTIfwOnWBE5HZRPpxkbTEOVv6QbA7VbTxCdNQMUOr5tpOeHSgCfpL7r0f+Goa8GhRLnN9aXmVgLAoGAALD+hZYOaGIDwDJqVTIiYlN41HHh8a8gyNpDH3+SQa5FaVMb3x43WZg0xToq3bz/hGEZRblOkRlNm9ikUzk+VkNnGSsWTJZ8TxHuIpd1js7GP3jexiuecVAc4jVLLpnbX0RZgFCSXZ9hq2Qs6MvOwrdga0n/6bNs8yD/UTcSgJUCgYEAnT54GqIKucnYqGCk2Y5l3lrOfABW3F+LJL61xdtIv0Fx3JFVVHf416f8hgxbiT/q8eH8a6iQgjeNfUaLI14Hj80Xf0TMADlSu1c4bEzA/KP42NumZYTkZPUQgAFb2Gl0VbW7v8VNM0b2IcXTXOij3o8/Q2sMu9g2BIH54ii88Es=','1775303527992');
CREATE TABLE emi_calculations (

  id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL,

  principal REAL, annual_rate REAL, tenure_months INTEGER,

  emi REAL, total_amount REAL, total_interest REAL, calculated_at TEXT);
INSERT INTO emi_calculations VALUES(1,'Deba_exe',600000.0,11.25,60,13120.38,787223.08,187223.08,'2026-03-30T13:38:33.236406Z');
CREATE TABLE tax_calculations (

  id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL,

  gross_income REAL, regime TEXT, taxable_income REAL,

  total_tax REAL, net_income REAL, calculated_at TEXT);
INSERT INTO tax_calculations VALUES(1,'Deba_exe',750000.0,'new',675000.0,23400.0,726600.0,'2026-03-30T13:38:58.224708Z');
CREATE TABLE ci_calculations (

  id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL,

  principal REAL, annual_rate REAL, time_years REAL, frequency TEXT,

  final_amount REAL, interest_earned REAL, calculated_at TEXT);
CREATE TABLE salary_calculations (

  id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL,

  basic_salary REAL, gross_salary REAL, total_deductions REAL,

  net_salary REAL, calculated_at TEXT);
CREATE TABLE calc_history (

  id            INTEGER PRIMARY KEY AUTOINCREMENT,

  username      TEXT    NOT NULL,

  expression    TEXT    NOT NULL,

  result        TEXT    NOT NULL,

  calculated_at TEXT    NOT NULL

);
INSERT INTO calc_history VALUES(1,'Deba_exe','10+9','19.0','2026-03-30T13:37:59.059490200Z');
INSERT INTO calc_history VALUES(2,'Deba_exe','92*(1+0.15)','105.8','2026-03-30T14:57:30.878646700Z');
INSERT INTO calc_history VALUES(3,'Deba_exe','sin(30)+cos(30)','-0.8337801742052777','2026-04-01T22:53:39.197607400Z');
INSERT INTO calc_history VALUES(4,'Deba_exe','sind(90)+cosd(0)','2.0','2026-04-01T22:54:03.113696900Z');
INSERT INTO calc_history VALUES(5,'Deba_exe','complex_add(2+3i,1+2i)','3.0+5.0i','2026-04-02T21:13:50.563053200Z');
INSERT INTO calc_history VALUES(6,'Deba_exe','complex_add(2+1i,3+2i)','5.0+3.0i','2026-04-02T21:13:59.433449500Z');
INSERT INTO calc_history VALUES(7,'Deba_exe','complex_subtract(2+1i,3+2i)','-1.0-1.0i','2026-04-02T21:14:02.036068400Z');
INSERT INTO calc_history VALUES(8,'Deba_exe','conj(2+1i)','2.0-1.0i','2026-04-02T21:14:04.678234300Z');
INSERT INTO calc_history VALUES(9,'Deba_exe','real(2,1)','2.0','2026-04-02T21:14:05.949306Z');
INSERT INTO calc_history VALUES(10,'Deba_exe','imag(2,1)','1.0','2026-04-02T21:14:07.154239400Z');
INSERT INTO calc_history VALUES(11,'Deba_exe','csq(2,1)','3.0','2026-04-02T21:14:08.793100500Z');
INSERT INTO calc_history VALUES(12,'Deba_exe','sind(90)+tand(45)+cosd(0)','3.0','2026-04-02T21:22:07.907981900Z');
INSERT INTO calc_history VALUES(13,'Deba_exe','max(9,8,4,1)','9.0','2026-04-02T21:22:21.800347200Z');
INSERT INTO calc_history VALUES(14,'Deba_exe','sinhd(2)','0.034913674241017253','2026-04-02T21:27:10.560368Z');
INSERT INTO calc_history VALUES(15,'Deba_exe','tand(90)','1.633123935319537E16','2026-04-02T21:27:20.499538400Z');
INSERT INTO calc_history VALUES(16,'Deba_exe','55555555^333333333','Infinity','2026-04-02T21:27:33.474272700Z');
INSERT INTO calc_history VALUES(17,'Deba_exe','1/4','0.25','2026-04-06T15:31:30.632324200Z');
CREATE TABLE password_history (

  id              INTEGER PRIMARY KEY AUTOINCREMENT,

  username        TEXT    NOT NULL,

  hashed_password TEXT    NOT NULL,

  created_at      TEXT    NOT NULL

);
CREATE TABLE IF NOT EXISTS "password_table" (

userid INTEGER PRIMARY KEY AUTOINCREMENT, 

  username           TEXT NOT NULL,

  platform           TEXT NOT NULL,

  encrypted_password TEXT,

  hashed_password    TEXT,

  created_date       TEXT,

  UNIQUE (username, platform)

);
INSERT INTO password_table VALUES(1,'Deba_exe','github.com','GUtxgkz/vmdGJxpHmIwWUC3k60nZsq7dTIIH8OSqZYS1LZjzqb9JIu/Bsi8EGKZQgq79tcedFdWYSt4lnKRUhQTkcMb58Cx6hllM6oMlV+AreRwW0TQvQ3AE/7pU3CeYzROB42vveW+k1bYewAIifZyvwffJKoI8LxsSuy6xBADN9Iz60WvXHLvgj7S5cm9LGG7JQo2Lx2K9lPg0FSoLVveT073pL3SxRRnWGyNGKUuOJnegW+dCREVtoxaCKqivhdZQOx+VrxATifKO3tL0z6MRAeeMRxwXy8w+7+buUld0O9B5wxnI6mp7WkjQ9+VRBFXazK9BH3qSAlcITRFhJw==','$2a$12$CW10xSdfiymRTJg/fc7zMuh.NPlTkpYvC/LO3bz1suqALIJTVcUGi','1775303527992');
PRAGMA writable_schema=ON;
CREATE TABLE IF NOT EXISTS sqlite_sequence(name,seq);
DELETE FROM sqlite_sequence;
INSERT INTO sqlite_sequence VALUES('calc_history',17);
INSERT INTO sqlite_sequence VALUES('emi_calculations',1);
INSERT INTO sqlite_sequence VALUES('tax_calculations',1);
INSERT INTO sqlite_sequence VALUES('password_table',5);
INSERT INTO sqlite_sequence VALUES('generator_table',2);
PRAGMA writable_schema=OFF;

-- ── Sprint 20 additions: Infrastructure tables ──────────────────────────

CREATE TABLE IF NOT EXISTS regex_patterns (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT NOT NULL,
  pattern TEXT NOT NULL,
  description TEXT,
  category TEXT,
  example_string TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE(username, pattern)
);

CREATE TABLE IF NOT EXISTS schema_templates (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE,
  description TEXT,
  schema_json TEXT NOT NULL,
  category TEXT,
  is_public INTEGER DEFAULT 1,
  created_by TEXT,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS tool_recommendations (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT NOT NULL,
  tool_path TEXT NOT NULL,
  tool_name TEXT NOT NULL,
  usage_count INTEGER DEFAULT 1,
  last_used_at TEXT NOT NULL,
  UNIQUE(username, tool_path)
);
COMMIT;
