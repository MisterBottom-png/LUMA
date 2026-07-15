# LUMA Request Routing

Use this file to decide what workflow to run.

## Visual Fix

Trigger examples:

```text
center the circled nav buttons
fix this spacing
this text is too low
align this card
fix dark mode contrast in screenshot
```

Action:

```text
Inspect visual reference → locate code → fix directly → validate → report
```

Questions:

```text
Do not ask questions unless the screenshot/request is unclear.
```

## Bug Fix

Trigger examples:

```text
the reminder time is wrong
app crashes when opening Review
search does not find spaces
notification does not fire
```

Action:

```text
Identify expected vs actual → inspect related code → fix → regression check → report
```

Questions:

```text
Ask only if expected behavior is unclear.
```

## Small Implementation

Trigger examples:

```text
add date numbers under weekdays
group items in spaces by date
add a close button
make Settings categorized
```

Action:

```text
State assumptions → include/exclude scope → implement → validate → report
```

Questions:

```text
Ask only if multiple reasonable interpretations exist.
```

## Broad Feature Discovery

Trigger examples:

```text
I want a mini calendar
make Review easier
make Situation AI smarter
add language switching
make LUMA feel more premium
```

Action:

```text
Ask up to 5 questions → create V1 feature brief → wait for approval
```

Questions:

```text
Required before coding.
```

## UX/Product Exploration

Trigger examples:

```text
this screen feels confusing
how should this work?
what would make this better?
```

Action:

```text
Analyze → recommend options → propose V1 → optionally create implementation prompt
```

Questions:

```text
Ask only if user intent is unclear.
```

## Risky Technical Feature

Trigger examples:

```text
AI creates reminders
change date parser
add backend sync
add Google Calendar
migrate database
change notifications
```

Action:

```text
Risk review → ask clarifying questions → plan → wait for approval → implement if approved
```

Questions:

```text
Required unless the user already gave precise technical constraints.
```

## Review Only

Trigger examples:

```text
review this
find bugs
audit the UI
inspect current changes
```

Action:

```text
Inspect only → report issues → do not edit
```

## Learning Only

Trigger examples:

```text
update learning
what should the agents remember?
add this to agent memory
```

Action:

```text
Run luma-self-learning → update learning files only
```
