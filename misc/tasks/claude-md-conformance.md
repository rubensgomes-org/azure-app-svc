# Apply the revised CLAUDE.md across the project

`CLAUDE.md` was rewritten mid-session. Two rules are new and nothing in the
tree has been checked against them yet:

- Do not generate Javadoc for private members.
- Do not generate Javadoc for simple getters, setters, constructors, DTOs, or
  obvious methods.

The Documentation Standards section ("prefer self-documenting code", "no
comments that merely restate the code") is also new.

The eight Java source files and eight test files were read in full. The code
is already close to conformant: constructor injection is used, there is no
static state, no magic numbers, no method over 20 lines, exceptions are not
swallowed, `Objects.requireNonNull` guards the one public entry point that
needs it, and `@Slf4j` is used throughout. What follows is the full list of
what does NOT conform.

## Scope

In scope: the 16 `.java` files under `app/src`.

Deliberately OUT of scope, pending a separate decision -- see Open questions:
`app/build.gradle.kts` (425 of 772 lines are comments), `Dockerfile` (101 of
156), `docker-compose.yml`, and the three `application*.yml` files.

## Tasks

### Javadoc on private members

- [x] `GlobalErrorController.resolveStatus` -- drop the Javadoc block from this
      `private static` method. The method name and the `Optional` chain already
      say what it does; the fallback-to-500 behaviour is asserted by three
      tests in `GlobalErrorControllerTest.StatusFallback`.
- [x] `GlobalErrorController.attribute` -- same, `private static`.

### Javadoc on constructors and DTOs

- [x] `HelloWorldRestController` -- drop the Javadoc from the single
      constructor. "Creates a controller backed by the given service layer"
      restates the signature.
- [x] `MessageResponse` -- drop the paragraph explaining what a Java record is
      (generated accessor/`equals`/`hashCode`/`toString`, implicit finality).
      It documents the language, not this type. Keep the class summary,
      `@param message` and `@author`.

### Comments that restate

- [x] `GlobalErrorController` -- delete the `// NOTE:` block above
      `@RequestMapping`. The Javadoc directly above it already explains why the
      method list is broad, and the NOTE ends by pointing back at that Javadoc.
- [x] `HelloWorldService` -- drop the `<ul>` listing "it returns a model
      response type" and "it separates front-end web layer and back-end
      business domain layer". Both restate what the signature and package
      layout show. Keep the one-line summary.
- [x] `MessageResponseTest.toStringContainsTheMessage` -- drop the comment
      about what the format was "before it became a record". It documents a
      change already in git history.

### Java guidelines

- [x] `AppTest` -- replace `import static org.junit.jupiter.api.Assertions.*`
      with the single-member import. This is the only wildcard import in the
      tree.
- [x] `HelloWorldRestController` -- remove the redundant `@Autowired` on the
      sole constructor. Spring has injected single constructors without it
      since 4.3; the annotation adds nothing but is the kind of noise "clarity
      over cleverness" argues against. Keeps constructor injection intact.
- [x] `AppTest` -- add the class Javadoc. It is the only test class without
      one, so this is consistency rather than a new rule.

### Verify

- [x] `./gradlew spotlessApply` then `./gradlew build` -- confirm the format
      check, the tests and the 90% JaCoCo line/branch gate all still pass.
      Nothing here changes behaviour, so coverage must not move.

## Open questions

1. **`AppTest` uses field injection** (`@Autowired private ApplicationContext
   context`), which "Constructor injection only for dependencies. Avoid field
   injection." forbids. The recommendation is to LEAVE IT: constructor
   injection in a JUnit 5 test class needs `@TestConstructor` or a parameter
   resolver, which is more machinery than the rule is worth in a test. Say the
   word and it changes, but it is listed as a question, not a task.

2. **Build and config files are out of scope above.** `app/build.gradle.kts` is
   55% comments and `Dockerfile` 65%. Much of that prose explains genuinely
   non-obvious intent -- why `RUN true` separates the `COPY --from` pairs, why
   ANSI output is pinned off -- which the Documentation Standards rules
   explicitly protect. Auditing it is a much larger and more subjective job
   than the Java pass. Separate task if wanted.

## Review

Ten edits across six files: 41 lines deleted, 6 added. `./gradlew spotlessApply
build` is green -- 35 tests, 0 failures, and JaCoCo reports 100% line and 100%
branch, unchanged, because nothing here touches behaviour.

The two new Javadoc rules accounted for most of it. `GlobalErrorController` lost
the blocks on `resolveStatus` and `attribute`; both are `private static`, both
have names that say what they do, and the fallback-to-500 behaviour the prose
described is already pinned by three tests in `StatusFallback`. The prose was
the third copy. `HelloWorldRestController` lost the constructor block that
restated its own signature, and `MessageResponse` lost the paragraph explaining
what a Java record is -- that belongs in a language tutorial, not on a
four-field DTO.

The largest single deletion is the `// NOTE:` block in `GlobalErrorController`.
It argued, correctly and at length, that the enumerated HTTP method list must
not be narrowed -- and the Javadoc immediately above it had already made the
same argument, which is why the NOTE ended by pointing back at it. Deleting the
duplicate costs nothing: the reasoning survives, once, where a reader meets it
first.

Two edits were consistency rather than rule-following. `AppTest` had the only
wildcard import in the tree and was the only test class without a class
Javadoc; both are fixed. The redundant `@Autowired` came off
`HelloWorldRestController`'s sole constructor -- Spring has not needed it since
4.3 -- and its now-unused import went with it. Constructor injection is intact.

One thing worth recording: `./gradlew :app:javadoc` emits five "use of default
constructor, which does not provide a comment" warnings. They are PRE-EXISTING
and unrelated to this pass -- they name the five classes that declare no
constructor at all (`App`, both event listeners, `HelloWorldService`,
`GlobalErrorController`). `HelloWorldRestController`, the one class whose
constructor Javadoc was removed here, does not warn, because its constructor is
still explicit. Removing a comment from a real constructor cannot produce a
default-constructor warning.

Left alone, as agreed: the field injection in `AppTest`, and the build and
config files (`app/build.gradle.kts`, `Dockerfile`, `docker-compose.yml`, the
`application*.yml` set).
