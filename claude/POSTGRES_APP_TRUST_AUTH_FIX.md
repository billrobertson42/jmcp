# Postgres.app Trust Authentication Error - Resolution Guide

## Problem
When connecting to PostgreSQL via Postgres.app, you receive:
```
FATAL: Postgres.app rejected "trust" authentication
Detail: You did not allow idea to connect without a password.
Hint: Configure app permissions in Postgres.app settings
```

## Root Cause
Postgres.app uses "trust" authentication by default in `pg_hba.conf`, which allows connections without passwords. However, Postgres.app 2.x+ has a security feature that **blocks applications from using trust authentication** unless explicitly permitted.

Even when you provide username/password in your configuration, if the PostgreSQL server is configured for "trust" authentication, it attempts to connect without credentials first, triggering the block.

## Solutions

### Solution 1: Modify pg_hba.conf (Recommended)
Change PostgreSQL to require password authentication:

1. **Locate pg_hba.conf:**
   ```bash
   ~/Library/Application Support/Postgres/var-XX/pg_hba.conf
   ```
   (Replace XX with your PostgreSQL version number)

2. **Edit the file** and find lines like:
   ```
   host    all             all             127.0.0.1/32            trust
   host    all             all             ::1/128                 trust
   ```

3. **Change `trust` to `md5`:**
   ```
   host    all             all             127.0.0.1/32            md5
   host    all             all             ::1/128                 md5
   ```
   
   Or use the more secure `scram-sha-256`:
   ```
   host    all             all             127.0.0.1/32            scram-sha-256
   host    all             all             ::1/128                 scram-sha-256
   ```

4. **Restart PostgreSQL** in Postgres.app (click the elephant icon → Quit, then relaunch)

5. Your config.json can now use the standard format:
   ```json
   {
     "id": "local_postgres",
     "databaseType": "postgresql",
     "jdbcUrl": "jdbc:postgresql://localhost/dot",
     "username": "your_username",
     "password": "your_password"
   }
   ```

### Solution 2: Add Credentials to JDBC URL
Include username and password as URL parameters:

```json
{
  "id": "local_postgres",
  "databaseType": "postgresql",
  "jdbcUrl": "jdbc:postgresql://localhost/dot?user=your_username&password=your_password&ssl=false",
  "username": "your_username",
  "password": "your_password"
}
```

Note: This may not work if Postgres.app blocks trust auth before the JDBC driver can pass credentials.

### Solution 3: Allow App in Postgres.app Settings
1. Open **Postgres.app**
2. Go to **Preferences** → **App Permissions**
3. Add your JVM or application to the allowed list

Note: This is less secure and not recommended.

## Verification
After applying the fix, test your connection. The enhanced error logging will now show:
- All connection parameters
- Specific guidance if trust authentication errors are detected

## Additional Notes
- **SSL:** Local connections typically don't need SSL. Use `?ssl=false` in the JDBC URL if you encounter SSL errors.
- **Port:** Postgres.app may use port 5433 instead of the default 5432. Check Postgres.app preferences.
- **Multiple Versions:** If you have multiple PostgreSQL versions installed, ensure you're editing the correct pg_hba.conf file.

