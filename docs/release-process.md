# Release Process

## Versioning

Follow [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`

- **MAJOR**: Breaking API changes or major architectural shifts.
- **MINOR**: New features, backward-compatible.
- **PATCH**: Bug fixes, dependency updates, minor improvements.

## Branch Strategy

```
main (production-ready)
 └── feature/*, bugfix/* (short-lived, merge via PR)
```

- `main` is always deployable.
- All changes go through pull requests.
- No direct pushes to `main`.

## Release Steps

### 1. Prepare

```bash
# Update version in pom.xml
mvn versions:set -DnewVersion=1.1.0

# Verify
mvn clean verify
```

### 2. Tag & Build

```bash
git add pom.xml
git commit -m "chore: bump version to 1.1.0"
git tag v1.1.0
git push origin main --tags

# Build release artifact
mvn clean package -DskipTests
```

### 3. Deploy

1. Upload the assembly archive (`target/expensetracker-<version>.zip` or equivalent).
2. Stop the running application.
3. Back up the database: `pg_dump -Fc expensetracker > backup_$(date +%Y%m%d).dump`.
4. Extract new release, update config if needed.
5. Start the application.
6. Flyway runs pending migrations automatically on startup.
7. Verify health endpoint: `curl http://localhost:8080/actuator/health`.

### 4. Post-Deploy

- Smoke test critical flows (login, create expense, upload receipt).
- Monitor logs for errors (`logs/expensetracker-error.log`).
- If critical issues, rollback: restore DB backup, redeploy previous version.

## Rollback

1. Stop application.
2. Restore database: `pg_restore -d expensetracker backup.dump`.
3. Deploy previous jar/assembly.
4. Start application.

> ⚠️ Flyway migrations are forward-only. If a migration must be undone, write a corrective migration or restore from backup.

## Checklist

- [ ] All tests pass (`mvn verify`).
- [ ] No critical/high SonarQube issues.
- [ ] Database migrations tested on staging.
- [ ] Changelog/release notes written.
- [ ] Config changes documented.
- [ ] Database backed up before deploy.
- [ ] Health check passes post-deploy.

