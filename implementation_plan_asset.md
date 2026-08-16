# Asset Manager Plugin: Architecture & Implementation Plan (Updated Specification)

This document details the architecture and complete implementation specifications for the **Asset Manager** plugin in the Nexa Framework. It includes complete JSON schemas, precise scripting API definitions, value-access scopes, and a detailed performance and scaling guide.

---

## 📄 Complete Configuration Schemas

To ensure consistency, every attribute in the asset configuration follows a uniform schema. An attribute can be either **static** (read/write manually or via flows) or **calculated** (using a script).

### 1. Dedicated Asset Workspace Schema (`workspace-assets.json`)
Here is the fully populated specification for the asset configuration file:

```json
{
  "id": "workspace-assets-production",
  "templates": [
    {
      "name": "MotorTemplate",
      "attributes": [
        {
          "name": "temperature",
          "dataType": "FLOAT32",
          "value": 0.0,
          "calculationConfig": null
        },
        {
          "name": "tempFahrenheit",
          "dataType": "FLOAT32",
          "value": 32.0,
          "calculationConfig": {
            "triggerType": "ON_CHANGE",
            "intervalExpr": null,
            "script": "return assetManager.read(\"../temperature\") * 1.8 + 32.0;"
          }
        },
        {
          "name": "runHours",
          "dataType": "FLOAT64",
          "value": 0.0,
          "calculationConfig": {
            "triggerType": "INTERVAL",
            "intervalExpr": "1s",
            "script": "if (assetManager.read(\"../status\") == \"RUNNING\") { return self.value + (1.0 / 3600.0); } else { return self.value; }"
          }
        },
        {
          "name": "status",
          "dataType": "STRING",
          "value": "STOPPED",
          "calculationConfig": {
            "triggerType": "ON_WRITE",
            "intervalExpr": null,
            "script": "val valUpper = self.newValue.toUpperCase(); if (valUpper == \"RUNNING\" || valUpper == \"STOPPED\" || valUpper == \"FAULT\") { return valUpper; } else { return self.value; }"
          }
        }
      ]
    }
  ],
  "assets": [
    {
      "name": "SiteA",
      "children": [
        {
          "name": "Line1",
          "children": [
            {
              "name": "Motor1",
              "template": "MotorTemplate",
              "attributes": [
                {
                  "name": "location",
                  "dataType": "STRING",
                  "value": "Section-102",
                  "calculationConfig": null
                }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

---

## 💻 Scripting API & Value Context Specifications

To make writing calculation scripts simple and powerful, the Nexa DSL script engine is extended with specialized asset helper functions.

### 1. Value Context Variables
Inside any attribute script, the engine binds the following context properties under the `self` object automatically:

| Property Name | Type | Description | Availability |
| :--- | :--- | :--- | :--- |
| `self.value` | `Object` | The current value of the attribute. | All trigger types |
| `self.oldValue` | `Object` | The previous value of the attribute before the current state. | All trigger types |
| `self.newValue` | `Object` | The new incoming raw value written to the attribute. | **`ON_WRITE` trigger only** (used to intercept, validate, or sanitize input value) |
| `self.timestamp` | `Long` | The epoch millisecond of the last update. | All trigger types |
| `self.quality` | `String` | The quality state (`GOOD`, `BAD`, `UNCERTAIN`). | All trigger types |

### 2. Global Script APIs (`assetManager`)
Available to both asset calculations and workspace flow nodes:

#### `assetManager.read(path: String): Object`
Returns the **raw value** (e.g., `23.5`, `true`, or `"RUNNING"`) directly, allowing easy inline calculations (e.g., `read("val") + 10`).

#### `assetManager.readVTQ(path: String): Map<String, Object>`
Returns a map containing the full details of the target attribute:
```json
{
  "value": 150.2,
  "oldValue": 149.8,
  "timestamp": 1781298371900,
  "quality": "GOOD"
}
```

#### `assetManager.write(path: String, value: Object): Boolean`
Writes a raw value to the target attribute.
*   **Restriction**: If called within an attribute calculation script (`ON_CHANGE` / `INTERVAL`), this will throw a runtime exception to prevent circular writes. It is fully allowed in flow executor scripts or `ON_WRITE` trigger scripts.

---

## ⚡ Performance, Scaling & Interval Recommendations

When dealing with large-scale industrial deployments (tens or hundreds of thousands of attributes), calculation frequency and scheduling strategies are critical.

### 1. Interval Speeds (How fast can we go?)
*   **Technical Limit**: The scheduler can execute down to **`10ms`** intervals.
*   **Recommended Minimum**: **`500ms`** (for high-speed tags like motor vibration or current spikes).
*   **Standard recommendation**: **`1s`** or higher for general tracking, metrics, and accumulated values (like running hours).

### 2. Scaling to 100,000+ Tags (How to keep it safe & performant?)
If you run 100,000 tags, executing them periodically can saturate CPU resources. We implement several engine optimizations to handle this safely:

1.  **Prefer `ON_CHANGE` Triggers**: Instead of polling every 100ms, use event-driven calculation. If Tag A only changes once every 5 seconds, an `ON_CHANGE` calculation on Tag B only executes once every 5 seconds, saving 98% of CPU cycles compared to a `100ms` interval.
2.  **Tick-Group Batching**: The Asset Manager does *not* create a separate Java timer for each tag. Instead, we group calculations by interval (e.g., all "1s" tags go into the "1-second tick group"). A single thread-pool manager executes the batch using virtual threads, minimizing scheduler overhead and context switching.
3.  **AST Caching**: Script parsing and compiling (into abstract syntax trees) is performed once during configuration load. At runtime, the pre-compiled AST is executed directly in memory, bypassing expensive parsing overhead.
4.  **Virtual Thread Safety**: Calculations are lightweight CPU operations. Although Virtual Threads are perfect for blocking I/O, CPU-bound calculation loops are run on a structured executor to prevent Carrier Thread starvation.

---

## 📅 Roadmap: Step-by-Step Implementation

### 🏁 Phase 1: Core Hierarchy, Templates, & Scripting Context (Next Step)
1.  Initialize the new module `nexa-asset-manager`.
2.  Implement parsing of `workspace-assets.json` configuration via Jackson.
3.  Implement the in-memory `Asset` tree structure.
4.  Implement the scripting context:
    *   Map variables (`self.value`, `self.oldValue`, `self.newValue`, `self.timestamp`, `self.quality`).
    *   Expose `assetManager` with `read`, `write`, and `readVTQ`.
    *   Implement relative path resolution logic.
5.  Add unit tests validating configuration parsing, parameter substitution, and formula execution.

### 🏁 Phase 2: Script Triggers (Interval, On Change, On Write)
1.  Implement the `INTERVAL` trigger using a batch Tick-Group Scheduler.
2.  Implement the `ON_CHANGE` trigger by tracking dependencies.
3.  Implement the `ON_WRITE` interceptor trigger to process scripts before committing values.
