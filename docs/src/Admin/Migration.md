# Migration guide

This page contains all the information you need to deal with breaking changes between versions.

Not that EpiLink is not a stable program yet. While it is usable in production, version updates are not guaranteed to be backwards compatible. We try our best to minimize the breakage, but it may happen regardless of our intentions as we grow and learn as programmers and undo the mistakes of the past.

Please refer to our [changelog](https://github.com/EpiLink/EpiLink/blob/dev/CHANGELOG.md) for more information on what changed. If you believe a change is a breaking change while it is not marked as such, please [open an issue](https://github.com/EpiLink/EpiLink/issues) to let us know about it!

## 0.6.x to 0.7.0

The following changes are breaking changes that will affect your configuration:

### From `roles` to `requires`

The previous `roles` fields have been removed. It allowed you to configure how each role was supposed to be triggered by a specific rule, with an additional (unused) display name. This has changed to a new field named `requires`, which is a list of the rules that need to be run in order to determine which roles need to be assigned to a user.

For example, this:

```yaml
roles:
  - name: myRole
    displayName: My Role
    rule: MyRule
  - name: anotherRole
    displayName: Another Role
    rule: MyRule
  - name: somethingElseWithADifferentRule
    displayName: Something Else
    rule: DifferentRule
```

Becomes this:

```yaml
requires: [MyRule, DifferentRule]
```

This is significantly easier to write and potentially removes tens (or hundreds!) of config lines.
