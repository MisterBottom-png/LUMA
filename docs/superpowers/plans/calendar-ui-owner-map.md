# Calendar UI owner map

- Calendar screen and control card: `app/src/main/java/com/orbit/app/ui/screens/calendar/CalendarScreen.kt` (`CalendarScreen`).
- Calendar state holder: `CalendarViewModel.kt` (`CalendarViewModel`, `CalendarUiState`); `selectedDate` is persisted for Day content, paging, labels, and Add routing.
- Month presentation model: `CalendarMonthGrid.kt` (`CalendarMonthCell`, `buildCalendarMonthGrid`).
- Month date cell: `CalendarScreen.kt` (`CalendarMonthDayCell`), with local interaction source.
- Day timeline and current-time indicator: `CalendarDayTimelineView.kt` (`CalendarDayTimelineView`, `CalendarTimelineCanvas`).
- Shared typography and dimensions: `ui/theme/Type.kt` and `ui/theme/DesignTokens.kt`.
- Calendar unit tests: `app/src/test/java/com/orbit/app/ui/screens/calendar/`; Compose accessibility test: `app/src/androidTest/java/com/orbit/app/ui/screens/calendar/CalendarScreenAccessibilityTest.kt`.

Month-date taps call `CalendarViewModel.selectDate`: they update the viewed date, visible month, header, Day data, and Add date context.  The prior persistent Month-grid pill came only from passing that state to the month-cell presentation model; it is not a required navigation behavior.
