# Performance Optimization Design

## Objective

Optimize JDHelper app for maximum performance and smoothness without affecting existing functionality. All changes must be minimal, targeted, and independently testable.

## Scope

9 identified performance issues across the codebase, categorized by priority:

### 🔴 High Priority (5 items)

| # | File | Issue | Fix |
|---|------|-------|-----|
| 1 | `LogConsole.kt` | Creates new `CoroutineScope` per `log()` call → GC pressure | Use shared singleton `scope` |
| 2 | `FloatingService.kt:329-331` | Reads SharedPreferences + creates `SimpleDateFormat` every 10ms in hot path | Cache millisecondDigits via Flow, reuse Format |
| 3 | `HomeViewModel.kt:364-383` | `setNextClickCountdown` launches new `while(true)` without cancelling previous → coroutine leak | Cancel previous job before launching new |
| 4 | `HomeViewModel.kt:103-114` | `startStatusRefreshTimer` polls `getEnabledAccessibilityServiceList` (IPC) every 2s forever | Remove timer, rely on Lifecycle ON_RESUME |
| 5 | `FloatingService.kt` / `FloatingMenuService.kt` | Fire-and-forget `CoroutineScope(Dispatchers.IO).launch{}` in `onCreate` → no cancellation on destroy | Use class-level scope + cancel in onDestroy |

### 🟡 Medium Priority (4 items)

| # | File | Issue | Fix |
|---|------|-------|-----|
| 6 | `DefaultTimeService.kt` / `TopStatusBar.kt` | Dead `when` branches (both paths → JD) | Simplify to direct calls |
| 7 | `FloatingService.kt` + `FloatingMenuService.kt` | Duplicate JD time sync on startup | Add 5s debounce in JdTimeService |
| 8 | `FloatingMenuService.kt:1078-1093` | `recordHistory` reads Room DB before every write | Cache `recordHistory` flag |
| 9 | `FloatingMenuService.kt` gift workflow | Frequent redundant `withContext(Dispatchers.Main)` switches | Remove unnecessary context switches |

## Design Principles

- **Minimal changes**: No architectural refactoring, no new abstractions
- **No side effects**: Each fix addresses one specific bottleneck
- **Reversible**: Each fix is a simple, localized edit
- **Risk-controlled**: No changes to click logic, timing, or service lifecycle

## Implementation Order

Execute in priority order (1→9). Each fix can be tested independently before proceeding to the next.

## Testing

- Build: `./gradlew assembleDebug` must succeed
- Compose UI integration: verified at each step
- Manual verification: floating clock updates, menu operations, click scheduling
