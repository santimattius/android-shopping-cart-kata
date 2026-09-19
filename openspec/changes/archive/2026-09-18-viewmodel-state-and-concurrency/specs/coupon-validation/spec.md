# Delta for coupon-validation

Only "Confirm Purchase gating" changes: the navigation handoff's delivery mechanism moves from a replay-zero `SharedFlow` (which can drop `NavigateToSummary` across a configuration-change resubscription gap) to a buffered `Channel` exposed as `receiveAsFlow()`. The coupon input holder migrating into `CartViewModel`'s combined state reduction is an internal production-mechanism change with no observable effect on this domain's other requirements ("Apply triggers remote-only validation", "Coupon validation result modeling", "Category-scoped discount calculation"); no delta is written for them.

## MODIFIED Requirements

### Requirement: Confirm Purchase gating

The system MUST gate navigation to Summary on the current coupon state without an unnecessary remote call. The navigation handoff MUST be delivered through a buffered, single-consumer channel (`Channel(Channel.BUFFERED)` exposed as `receiveAsFlow()`) and MUST be delivered exactly once to that consumer, surviving a transient gap where the consumer is not actively collecting (e.g. a configuration-change resubscription window). This channel is fan-out to one collector, not a broadcast; introducing a second concurrent collector requires revisiting this requirement.
(Previously: gating logic only; delivery used an unspecified `MutableSharedFlow(extraBufferCapacity = 1)` that could drop the event if no collector was attached at emission time.)

#### Scenario: Empty field bypasses validation

- GIVEN the coupon field is empty
- WHEN the user taps Confirmar Compra
- THEN no coupon call is made and navigation proceeds with 0% discount

#### Scenario: Already-applied valid coupon

- GIVEN Aplicar already returned Valid for the typed code
- WHEN the user taps Confirmar Compra
- THEN navigation proceeds using that result, without a new remote call

#### Scenario: Typed but not yet applied

- GIVEN a code is typed and Aplicar was not pressed
- WHEN the user taps Confirmar Compra
- THEN the system validates remotely first, then navigates or blocks per the outcome

#### Scenario: Invalid, inactive, or errored coupon blocks navigation

- GIVEN the coupon state is Invalid, Inactive, or ServiceError
- WHEN the user taps Confirmar Compra
- THEN navigation MUST NOT occur and the error message stays visible

#### Scenario: Navigation event survives a consumer resubscription gap

- GIVEN Confirm Purchase succeeds and emits NavigateToSummary while the screen's collector is transiently detached (e.g. mid configuration change)
- WHEN the screen recreates and resubscribes to the event stream
- THEN the buffered NavigateToSummary event is still delivered exactly once
- AND no second collector receives a duplicate of the same event
