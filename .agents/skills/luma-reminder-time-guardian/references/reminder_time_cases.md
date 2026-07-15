# Reminder Time Cases

```text
Input: send package tomorrow at 10, remind me one hour earlier
Target: tomorrow 10:00
Notification: tomorrow 09:00
Needs confirmation: yes if extracted by AI and user has not confirmed
```

```text
Input: remind me tomorrow
Missing: time
Action: ask missing-information prompt
```

```text
Input: remind me in the morning
Missing: exact time unless user preference exists
Action: ask or apply explicit app preference
```
