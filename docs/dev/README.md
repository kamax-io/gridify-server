### Tests
#### PostgreSQL integration
Via env variable, adapt as needed:
```bash
export GRIDIFY_TEST_STORE_POSTGRESQL_CONFIG='{"type":"postgresql","connection":"//localhost/grid?user=grid&password=grid"}'
./gradlew test
```
