# Course Prerequisite Path Planner

A web application that generates all possible course plans to reach a target class, given a student's completed coursework. Built for Cornell University's course catalog.

## Overview

Students often struggle to figure out which courses they need to take before enrolling in an upper-level class. This tool solves that problem by:

1. Modeling the entire course catalog as a prerequisite graph
2. Parsing complex prerequisite requirements (AND/OR combinations, equivalencies)
3. Generating all valid course sequences to reach a target course
4. Pruning redundant paths based on already-completed courses

## Features

- **Path Generation**: Finds all possible routes to a target course
- **Equivalency Handling**: Recognizes that CS 1110 and CS 1112 satisfy the same requirement
- **Smart Pruning**: If you've taken CS 2110, it knows you've satisfied CS 1110/1112 transitively
- **OR Branch Optimization**: Prefers real courses over "permission of instructor" alternatives
- **Topological Ordering**: Uses DFS post-order traversal to output courses in the correct order, with SPECIAL/PERMISSION requirements placed immediately before the course that needs them

## Example

```
Target: CS 4820
Already taken: [CS 1110, MATH 1110]

Found 1 plan(s):

Plan 1 (4 courses):
  CS 2110 → CS 2800 → CS 3110 → CS 4820
```

```
Target: CS 3780
Already taken: []

Found 20 plan(s):

Plan 1 (7 courses):
  CS 1110 → CS 2110 → SPECIAL REQUIREMENT: HIGH SCHOOL MATH BACKGROUND → MATH 1110 → CS 2800 → MATH 2940 → CS 3780

Plan 2 (7 courses):
  CS 1110 → CS 2110 → SPECIAL REQUIREMENT: HIGH SCHOOL MATH BACKGROUND → MATH 1110 → CS 2800 → MATH 2310 → CS 3780

Plan 3 (7 courses):
  CS 1112 → CS 2110 → SPECIAL REQUIREMENT: HIGH SCHOOL MATH BACKGROUND → MATH 1110 → CS 2800 → MATH 2940 → CS 3780

...
```

## Architecture

The project is split into two packages:

### `prereqsolver.runtime` — Used at request time
| Class | Purpose |
|-------|---------|
| `PlanFinder` | Main algorithm — traverses prerequisite trees, generates plans |
| `Plan` | Represents a course sequence with ordering |
| `PrereqData` | Loads TSV, caches parsed requirement trees |
| `TokenStringParser` | Parses token strings into AST |
| `Requirement` | Interface for AST nodes |
| `Expression` | AND/OR branch node |
| `Unit` | Leaf node (single course or special requirement) |
| `TokenType` | Enum for AND, OR, COURSE, etc. |

### `prereqsolver.pipeline` — Used once per semester to build the TSV
| Class | Purpose |
|-------|---------|
| `CatalogLoader` | Parses Cornell course catalog JSON |
| `Tokenizer` | Converts raw prerequisite strings to tokens |
| `PrereqProcessor` | Orchestrates the tokenization pipeline |
| `Token` | Token record (type + literal) |

## Algorithm

### Data Flow
```
Cornell API → JSON → CatalogLoader → Tokenizer → TSV (manual review) → PrereqData → PlanFinder
```

### Prerequisite Parsing

Raw prerequisite strings like:
```
"CS 2110 or CS 2112, and MATH 1920 or MATH 2220"
```

Are tokenized into:
```
LPAREN COURSE(CS 2110) OR COURSE(CS 2112) RPAREN AND LPAREN COURSE(MATH 1920) OR COURSE(MATH 2220) RPAREN
```

Then parsed into an AST:
```
        AND
       /   \
      OR    OR
     / \   /  \
 2110 2112 1920 2220
```

### Path Generation

The `PlanFinder` uses **post-order traversal** on the AST:

- **Unit (leaf)**: Return a plan containing just this course (after recursing into its prereqs)
- **OR node**: Return union of plans from both branches (alternatives)
- **AND node**: Return Cartesian product of plans from both branches (must satisfy both)

Key optimizations:
1. **Already-satisfied pruning**: If an OR branch is already satisfied by taken courses, skip exploring alternatives
2. **Direct SPECIAL pruning**: If an OR has a real course vs "permission of instructor", prefer the real course
3. **Context passing**: When processing AND nodes, pass the left branch's courses to the right branch to avoid duplicates
4. **Topological sort**: After generating plans, DFS post-order traversal ensures prereqs come before courses that need them

### Equivalency Handling

When you've taken a course, we recursively mark all its prerequisites as "effectively taken":

```java
// If you took CS 2110, you must have satisfied (CS 1110 OR CS 1112)
// So we add BOTH to takenCourses — neither needs to appear in future plans
expandTakenCourses();
```

## Tech Stack

- **Backend**: Java 21
- **Frontend**: React (hosted on GitHub Pages)
- **Deployment**: Google Cloud
- **Data**: Cornell Course Roster API

## Data Pipeline

1. **Fetch**: Python script pulls course catalog JSON from Cornell API
2. **Tokenize**: Java `Tokenizer` converts prerequisite strings to token sequences
3. **Review**: Output TSV opened in spreadsheet for manual correction of edge cases
4. **Runtime**: Corrected TSV loaded by `PrereqData` at startup

Over 1,000 courses with prerequisites, ~300 manually corrected for complex edge cases.

## Usage

### Running the Backend

```bash
cd prereqsolver
javac -d out prereqsolver/runtime/*.java prereqsolver/pipeline/*.java
java -cp out prereqsolver.runtime.PlanFinder tokenized_prereqs_corrected.tsv
```

### API

```java
PrereqData data = new PrereqData("tokenized_prereqs_corrected.tsv");
Set<String> taken = Set.of("CS 1110", "MATH 1110");
PlanFinder finder = new PlanFinder(data, taken);

List<Plan> plans = finder.findPlans("CS 4820");
for (Plan plan : plans) {
    System.out.println(plan);  // CS 2110 → CS 2800 → CS 3110 → CS 4820
}
```

## Future Work

- [x] ~~Proper topological sort on merged plans~~
- [ ] Add more semesters to increase course coverage
- [ ] Credit hour tracking
- [ ] Semester-by-semester scheduling with concurrent course limits
- [ ] Support for other universities (UIUC)

## Authors

- **Wynn Shu** — Backend algorithm, data pipeline (Cornell '28)
- **Jerry [Last Name]** — Frontend, deployment (UIUC '28)

## License

MIT
