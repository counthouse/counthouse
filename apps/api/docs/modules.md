# API modules

Generated from the Spring Modulith modules by `ModularityTests.moduleDiagramMatchesTheCode`. When it fails, it writes the new diagram to `target/modules.md` and prints the command that copies it here.

```mermaid
flowchart LR
  m_identity["identity"]
  m_problems["problems"]
  m_tenancy["tenancy"]
  m_identity --> m_tenancy
```
