# Second pass: "Add and keep documentation that describes a design pattern"

`CLAUDE.md` Documentation Standards gained a generative rule:

> Add and keep documentation that describes a design pattern.

and `DTOs` was dropped from the Java Javadoc exclusion list, so pattern prose
on a record no longer collides with that exclusion.

This reverses two deletions from `claude-md-conformance.md` and adds pattern
documentation where a pattern is in use but undocumented. All 8 main and 8 test
sources were re-read.

## Restore (reverses the previous pass)

- [x] `HelloWorldService` -- restore the `<ul>` verbatim. It is headed "This
      class uses the following patterns"; the new rule protects it by name.
- [x] `MessageResponse` -- restore the value-object paragraph in full. With the
      DTO exclusion gone there is no longer a reason to trim it.

## Add

Each addition states WHY the pattern was chosen. Naming a pattern that is
already obvious from the `implements` clause would be a restatement, which the
same section still forbids.

- [x] `AppInitEventListener` -- Observer. Proposed text:

      <p>Implements the observer pattern through Spring's {@link
      ApplicationListener} contract: the container publishes lifecycle events
      and this bean is notified, rather than {@code App.main} querying the
      server for its port after startup. Startup reporting therefore stays out
      of the bootstrap path and can be removed by deleting one class.

- [x] `AppShutdownEventListener` -- Observer, same rationale. Proposed text:

      <p>The shutdown half of the observer pattern described on {@link
      AppInitEventListener}: {@link ContextClosedEvent} is published on SIGTERM,
      so orderly-shutdown logging is a subscriber rather than a shutdown hook
      registered by hand.

- [x] `ErrorResponse` -- value object. Proposed text, appended after the
      existing compatibility paragraph:

      <p>A value object, declared as a record so the accessors, {@code equals},
      {@code hashCode} and {@code toString} are generated and the components are
      final. The type carries no behaviour: it exists to give the error payload
      a name and a fixed shape.

- [x] `GlobalErrorController` -- names the two patterns already at work.
      Proposed text, appended to the existing class Javadoc:

      <p>Two patterns are at work. The single {@code /error} mapping is the
      centralised error-handling pattern -- one place renders every failure,
      instead of each handler formatting its own. {@link #UNKNOWN_PATH} and
      {@link #NO_DETAIL_MESSAGE} are null objects: a missing attribute yields a
      harmless stand-in, so no caller downstream has to null-check.

## Not changing

- [x] `HelloWorldRestController` -- already documents its delegation pattern.
      No edit.
- [x] `App` -- a Spring Boot entry point is framework bootstrap, not a design
      pattern. No edit.
- [x] Everything else from the previous pass stands: the two `private static`
      Javadoc blocks, the duplicated `// NOTE:` block, the constructor Javadoc,
      the `MessageResponseTest` history comment, the wildcard import, the
      redundant `@Autowired`. None is pattern documentation.

## Verify

- [x] `./gradlew spotlessApply build` -- Google Java Format reflows Javadoc, so
      the restored blocks must be run through it. Confirm 35 tests still pass
      and the JaCoCo gate holds. No behaviour changes, so coverage must not move.

## Open question

Tests are excluded above. `GlobalErrorControllerTest.requestWith` is a test
fixture factory and `AppMainTest` already explains its static-mock approach.
Extending pattern documentation into the test tree would add prose to code
whose `@DisplayName` strings already carry the intent. Say the word if you want
tests in scope.

## Review

Six edits across six files. `./gradlew spotlessApply build` is green -- 35
tests, 0 failures, JaCoCo unchanged at 100% line and 100% branch, since nothing
here touches behaviour.

The two restorations put back exactly what the previous pass removed.
`HelloWorldService` regains its pattern list and `MessageResponse` its
value-object paragraph, both verbatim. The previous pass had a defensible
reading of the rules as they stood; the rules changed, so the reading changed.

The four additions each argue WHY rather than naming a pattern. Writing "this
implements the observer pattern" above `implements ApplicationListener` would
have been a restatement, which Documentation Standards still forbids. So the
listeners explain what the alternative would have cost -- `App.main` querying
the server for its port, a shutdown hook registered by hand -- and
`AppShutdownEventListener` cross-references the init listener instead of
repeating the argument. `GlobalErrorController` names centralised error
handling and, for the two fallback constants, the null object pattern.

### Correction to the previous pass's Review

That Review claimed the five `javadoc` warnings were all pre-existing. That was
established by grepping for one warning string, "use of default constructor",
and finding five. The build output said SIX. The sixth is
`HelloWorldRestController.java:32: warning: no comment` -- on the constructor
whose Javadoc that pass removed. It is NEW, and the earlier claim was wrong.

This is a real conflict, not a mistake in the edit. `CLAUDE.md` says not to
generate Javadoc for constructors; the `javadoc` task, which runs with doclint
defaults and no suppression configured in `app/build.gradle.kts`, warns when a
public constructor has no comment. Following the rule produces the warning.
The build passes either way -- it is a warning, not an error -- so this is left
as a decision rather than silently resolved. The options are to accept the
warning, to configure `-Xdoclint:all,-missing` on `tasks.javadoc`, or to keep a
one-line constructor comment and accept the rule exception.
