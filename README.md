# Dynamic JSON Transformer Engine

A lightweight, highly configurable Spring Boot microservice designed to transform arbitrary, unstructured, or legacy JSON payloads into standardized target schemas using a declarative, model-driven mapping approach.

---

## 📌 Overview & Problem Statement

In enterprise environments and data integration pipelines, external systems, legacy services, or OCR/document extraction engines often emit heterogeneous JSON structures featuring:
- Non-standard, legacy, or language-specific keys (e.g., `"Art. Cliente"`, `"SPESS."`, `"U.M."`, `"QUANTITA'"`).
- Deeply nested properties or flat structures requiring hierarchical restructuring.
- Lists of heterogeneous objects requiring recursive traversal.
- Mixed primitive types (e.g., boolean values serialized as `"true"` / `"false"` strings).

**JSON Transformer Engine** eliminates the need to write custom DTO mappings or rigid deserializers for each incoming payload. Instead, it applies a recursive traversal algorithm backed by a declarative Spring Bean definition (`mappingModel`), ensuring seamless schema adaptation, key sanitization, and remote payload forwarding.

---

## 🚀 Key Features

- **Declarative Mapping Model:** Transformation rules are isolated as a configuration bean (`@Qualifier("mappingModel")`), separating mapping business rules from execution code.
- **Recursive & Hierarchical Traversal:** Full support for deeply nested JSON objects and arrays of arbitrary depth.
- **Cross-Level Field Resolution:** Fallback lookup to the root document (`rootInput`) when child objects require values declared at the parent level.
- **Target Directive Renaming:** Use of `_target` directives to rename object containers dynamically.
- **Type Normalization:** Automatic coercion of boolean strings (`"true"`, `"false"`) into native JSON booleans, preserving native numbers.
- **Remote Dispatch Pipeline:** Includes built-in HTTP client forwarding (`RestTemplate`) to dispatch transformed payloads to external endpoints with error tracking.

---

## 🏗️ Architecture & Component Workflow

jsontransformer/
├── pom.xml                               # Maven Project Descriptor
├── README.md                             # Documentation & Verification Guide
└── src/
    ├── main/
    │   ├── java/com/petito/jsontransformer/
    │   │   ├── JsonTransformerApplication.java # Spring Boot Main Class
    │   │   ├── config/
    │   │   │   └── AppConfig.java              # mappingModel & Server URL Definition
    │   │   ├── controller/
    │   │   │   └── JsonTransformerController.java # Ingress REST Controller
    │   │   └── service/
    │   │       └── JsonTransformerService.java    # Core Recursive Traversal Engine
    │   └── resources/
    │       └── application.properties             # Server Port & Remote Endpoints
    └── test/                                      # Unit and Integration Tests

    [ External Ingestion / OCR Systems / QA Tester ]
                                      │
                                      ▼ (HTTP POST :8080)
                       ┌──────────────────────────────┐
                       │   JsonTransformerController  │
                       │   - /api/v1/commit           │
                       │   - /api/v1/send             │
                       │   - /api/v1/transform        │
                       └──────────────┬───────────────┘
                                      │
                                      ▼
                       ┌──────────────────────────────┐
                       │   JsonTransformerService     │
                       │   - Recursive Map Traversal  │
                       │   - Cross-Level Root Fallback│
                       │   - Scalar Type Coercion     │
                       └──────────────┬───────────────┘
                                      │
              ┌───────────────────────┴───────────────────────┐
              ▼                                               ▼
  ┌──────────────────────────────┐            ┌──────────────────────────────┐
  │      @Bean mappingModel      │            │       Remote Dispatch        │
  │  Declarative Transformation  │            │  RestTemplate POST Forward   │
  │     Rule-Set Definition      │            │   (app.remote.base-url)      │
  └──────────────────────────────┘            └──────────────────────────────┘


Incoming JSON String
        │
        ▼
[ JsonTransformerController ] (/api/v1/commit, /api/v1/send)
        │
        ▼
[ JsonTransformerService ] ◄── Injected [ mappingModel Bean ]
        │
        ├── 1. Parse JSON to generic Map<String, Object> (Jackson ObjectMapper)
        ├── 2. Recursive traversal (Objects & Arrays)
        ├── 3. Field matching, renaming & scalar type conversion
        └── 4. Build sanitized, ordered Map (LinkedHashMap)
        │
        ▼
Transformed JSON Output / Remote HTTP Forwarding


*** TESTING APP ***

$uri = "http://localhost:8080/api/v1/commit"

$body = @"
{
  "status": true,
  "err": null,
  "company": {
    "id": "cm000001111fgthy",
    "name": "BAPAM",
    "vat": "111110000034567890"
  },
  "fields": [
    {
      "status": true,
      "err": null,
      "boxId": "Field Box",
      "raw": "<info>{\"DATA\":\"03-02-2025\",\"NUMERO\":\"181/VAST\"}</info>",
      "clean": "{\"DATA\":\"03-02-2025\",\"NUMERO\":\"201/VAST\"}"
    }
  ],
  "articles": [
    {
      "Art. Cliente": "BBBB3345GHT678",
      "Descrizione": "Oggetto Personale",
      "SPESS.": "45.0",
      "FOGLI": "56",
      "U.M.": "FN2",
      "QUANTITA'": "234.55"
    },
    {
      "Art. Cliente": "BV4556GTF45",
      "Descrizione": "Oggetto personale 2",
      "SPESS.": "22.0",
      "FOGLI": "99",
      "U.M.": "M2",
      "QUANTITA'": "692.366"
    },
    {
      "Art. Cliente": "MBPgggh657890P",
      "Descrizione": "Oggetto Personale 3",
      "SPESS.": "22.0",
      "FOGLI": "74",
      "U.M.": "M2",
      "QUANTITA'": "517.526"
    },
    {
      "Art. Cliente": "MBP3RTYUUYP",
      "Descrizione": "Oggetto Personale 4",
      "SPESS.": "22.0",
      "FOGLI": "25",
      "U.M.": "M2",
      "QUANTITA'": "174.840"
    },
    {
      "Art. Cliente": "SDFHAHJ",
      "Descrizione": "Oggetto Personale 5",
      "SPESS.": "20",
      "FOGLI": "M2",
      "U.M.": "KG",
      "QUANTITA'": "139.872"
    }
  ]
}
"@

$response = Invoke-RestMethod -Uri $uri `
  -Method Post `
  -Headers @{ "Content-Type" = "application/json; charset=utf-8" } `
  -Body $body

$response | ConvertTo-Json -Depth 10