// ===========================================================================
//  SmartPark - MongoDB accounts (run ONCE on the Admin PC)
//
//  How to run (either):
//    * MongoDB Compass > connect to mongodb://localhost:27017 > ">_ MONGOSH" panel at the
//      bottom > paste this whole file > Enter
//    * mongosh "mongodb://localhost:27017" --file create-smartpark-user.js
//
//  Replace both CHANGE-ME passwords first. Run it BEFORE enabling authorization in
//  mongod.cfg (see mongod.cfg.example); afterwards only these accounts can log in.
//
//  Accounts created:
//    smartparkAdmin  - administers users (for you, not used by the application)
//    smartpark       - used by the SmartPark backend; read/write on smartpark_db ONLY
// ===========================================================================

const ADMIN_PASSWORD = "CHANGE-ME-admin-password";
const APP_PASSWORD = "CHANGE-ME-app-password";
const APP_DB = "smartpark_db";

if (ADMIN_PASSWORD.startsWith("CHANGE-ME") || APP_PASSWORD.startsWith("CHANGE-ME")) {
  throw new Error("Edit the script and replace both CHANGE-ME passwords first.");
}

const admin = db.getSiblingDB("admin");
if (!admin.getUser("smartparkAdmin")) {
  admin.createUser({ user: "smartparkAdmin", pwd: ADMIN_PASSWORD, roles: [{ role: "userAdminAnyDatabase", db: "admin" }] });
  print("Created user smartparkAdmin (admin)");
} else {
  print("User smartparkAdmin already exists - unchanged");
}

const app = db.getSiblingDB(APP_DB);
if (!app.getUser("smartpark")) {
  app.createUser({ user: "smartpark", pwd: APP_PASSWORD, roles: [{ role: "readWrite", db: APP_DB }] });
  print("Created user smartpark (readWrite on " + APP_DB + ")");
} else {
  print("User smartpark already exists - unchanged");
}

print("");
print("Next: enable authorization in mongod.cfg, restart the MongoDB service, and set in");
print("application-local.properties (URL-encode special characters in the password):");
print("  spring.mongodb.uri=mongodb://smartpark:<password>@127.0.0.1:27017/" + APP_DB + "?authSource=" + APP_DB);
