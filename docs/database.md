# Database
## PostgreSQL
### Setup
After having installed the PostgreSQL server:
```bash
su - postgres
createuser --pwprompt gridifyd
psql
```
At the SQL prompt:
```sql
CREATE DATABASE "gridifyd"
 ENCODING 'UTF8'
 LC_COLLATE='C'
 LC_CTYPE='C'
 template=template0
 OWNER gridifyd;
```
And quit the prompt with:
```
exit;
```

In your `config.yaml` file (if needed, so your install instruction), assuming a local DB:
```yaml
storage:
  database:
    type: 'postgresql'
    connection: '//localhost/gridifyd?user=gridifyd&password=CHANGE-ME'
```
Change the password to what was provided to the `createuser` command
