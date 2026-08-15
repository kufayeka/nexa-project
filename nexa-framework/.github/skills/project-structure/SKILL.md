---
name: project-structure
description: Standard package organization/structure for Nexa modules.
---

Organize packages by domain, not by technical layer.

Preferred:

deployment/
execution/
workflow/
scheduler/
statistics/
scripting/
plugin/
workspace/

Inside each domain:

controller/
service/
repository/
registry/
model/
exception/
config/
internal/

Avoid:

common/
util/
helper/
misc/

If a package becomes too generic, split it.

Controllers orchestrate.

Services contain business logic.

Repositories manage persistence.

Models contain state.

Registries resolve implementations.