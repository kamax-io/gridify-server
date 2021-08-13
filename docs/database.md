# Database
## PostgreSQL
### Setup
On Debian, after having installed the PostgreSQL server:
```bash
su - postgres
createuser --pwprompt gridify-server
psql
```
At the SQL prompt:
```sql
CREATE DATABASE "gridify-server"
 ENCODING 'UTF8'
 LC_COLLATE='C'
 LC_CTYPE='C'
 template=template0
 OWNER gridify-server;
```
And quit the prompt with:
```
\q
```

In your `gridify.yaml` config file (if needed, so your install instruction), assuming a local DB:
```yaml
storage:
  database:
    type: 'postgresql'
    connection: '//localhost/gridify?user=gridify&password=gridify'
```
Change the password to what was provided to the `createuser` command
