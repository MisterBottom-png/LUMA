# LUMA cleanup inventory

> Generated, non-destructive report. Every item is a candidate requiring evidence, not an instruction to delete.

## Repository

- Root: `C:\Users\User\Projects\orbit-android`
- Branch: `master`
- Commit: `09c56f0e1cc566f8bfeaca6bb5cea824e6b9d25e`
- Scanned regular files: 535
- Duplicate hash ceiling: 256 MiB per file

### Git status

```text
M .gitignore
 M AGENTS.md
 D FULL_BEGINNER_TUTORIAL.md
 D PACKAGE_CONTENTS.md
 D README_START_HERE.md
 M app/build.gradle.kts
 M app/src/main/AndroidManifest.xml
 M app/src/main/java/com/orbit/app/MainActivity.kt
 M app/src/main/java/com/orbit/app/OrbitApplication.kt
 M app/src/main/java/com/orbit/app/data/local/OrbitDatabase.kt
 M app/src/main/java/com/orbit/app/data/local/OrbitTypeConverters.kt
 M app/src/main/java/com/orbit/app/data/local/dao/OrbitDaos.kt
 M app/src/main/java/com/orbit/app/data/local/entity/OrbitEntities.kt
 M app/src/main/java/com/orbit/app/data/repository/AppSettingsRepository.kt
 M app/src/main/java/com/orbit/app/data/repository/EntityRepositories.kt
 M app/src/main/java/com/orbit/app/domain/analyzer/CaptureAnalyzer.kt
 M app/src/main/java/com/orbit/app/domain/model/AppSettings.kt
 M app/src/main/java/com/orbit/app/reminders/package-info.kt
 M app/src/main/java/com/orbit/app/ui/components/FloatingBottomNavigation.kt
 M app/src/main/java/com/orbit/app/ui/components/GlassSurface.kt
 M app/src/main/java/com/orbit/app/ui/components/OrbitBackground.kt
 M app/src/main/java/com/orbit/app/ui/navigation/OrbitApp.kt
 M app/src/main/java/com/orbit/app/ui/navigation/OrbitDestination.kt
 M app/src/main/java/com/orbit/app/ui/screens/home/CaptureSuggestionSheet.kt
 M app/src/main/java/com/orbit/app/ui/screens/home/HomeCaptureViewModel.kt
 M app/src/main/java/com/orbit/app/ui/screens/home/HomeScreen.kt
 M app/src/main/java/com/orbit/app/ui/screens/review/ReviewScreen.kt
 M app/src/main/java/com/orbit/app/ui/screens/settings/SettingsScreen.kt
 M app/src/main/java/com/orbit/app/ui/screens/situation/SituationAiSheet.kt
 M app/src/main/java/com/orbit/app/ui/screens/spaces/SpacesScreen.kt
 M app/src/main/java/com/orbit/app/ui/theme/Theme.kt
 M app/src/main/res/values/colors.xml
 M build.gradle.kts
 D checklists/android_quality_checklist.md
 D checklists/home_guardrails.md
 D checklists/mvp_acceptance_checklist.md
 D codex/agents/android_architect_agent.md
 D codex/agents/privacy_security_agent.md
 D codex/agents/product_developer_agent.md
 D codex/agents/professional_ui_designer_agent.md
 D codex/agents/qa_reviewer_agent.md
 D codex/agents/reminders_notifications_agent.md
 D codex/prompts/00_foundation_app_shell.md
 D codex/prompts/01_local_data_room.md
 D codex/prompts/02_capture_suggestion_flow.md
 D codex/prompts/03_capture_analyzer.md
 D codex/prompts/04_actions_notes_tasks_reminders.md
 D codex/prompts/05_real_reminders_notifications.md
 D codex/prompts/06_spaces.md
 D codex/prompts/07_review.md
 D codex/prompts/08_situation_ai.md
 D codex/prompts/09_appearance_settings.md
 D codex/prompts/10_monday_optional_integration.md
 D codex/prompts/11_product_developer_review.md
 D codex/prompts/12_ui_designer_review.md
 D codex/prompts/13_qa_subagents_review.md
 D codex/prompts/MASTER_BUILD_PLAN.md
 M gradle.properties
 M gradle/libs.versions.toml
 M gradlew
 M settings.gradle.kts
 D skills/orbit-ui/SKILL.md
 D specs/data_model.md
 D specs/notification_requirements.md
 D specs/orbit_product_spec.md
 D specs/orbit_technical_requirements.md
 D workflow/agent_workflow.md
 D workflow/windows_codex_tutorial.md
?? .agents/
?? .codex/
?? CLEANUP_BEFORE_LUMA_V4_UPGRADE_REPORT.md
?? _cleanup_before_luma_v4_upgrade/
?? app/schemas/com.orbit.app.data.local.OrbitDatabase/2.json
?? app/src/main/java/com/orbit/app/data/export/
?? app/src/main/java/com/orbit/app/domain/ai/
?? app/src/main/java/com/orbit/app/domain/analyzer/LocalReviewAnalyzer.kt
?? app/src/main/java/com/orbit/app/domain/analyzer/SituationAnalyzer.kt
?? app/src/main/java/com/orbit/app/domain/search/
?? app/src/main/java/com/orbit/app/domain/usecase/ConfirmCaptureActionUseCase.kt
?? app/src/main/java/com/orbit/app/domain/usecase/RecordAiLearningEventUseCase.kt
?? app/src/main/java/com/orbit/app/integrations/gemini/
?? app/src/main/java/com/orbit/app/reminders/ReminderAlarmReceiver.kt
?? app/src/main/java/com/orbit/app/reminders/ReminderBootReceiver.kt
?? app/src/main/java/com/orbit/app/reminders/ReminderNotificationWorker.kt
?? app/src/main/java/com/orbit/app/reminders/ReminderNotifications.kt
?? app/src/main/java/com/orbit/app/reminders/ReminderNotifier.kt
?? app/src/main/java/com/orbit/app/reminders/ReminderRescheduleWorker.kt
?? app/src/main/java/com/orbit/app/reminders/ReminderScheduler.kt
?? app/src/main/java/com/orbit/app/reminders/WorkManagerReminderScheduler.kt
?? app/src/main/java/com/orbit/app/security/GeminiApiKeyStore.kt
?? app/src/main/java/com/orbit/app/ui/screens/item/
?? app/src/main/java/com/orbit/app/ui/screens/review/ReminderDetailScreen.kt
?? app/src/main/java/com/orbit/app/ui/screens/review/ReminderDetailViewModel.kt
?? app/src/main/java/com/orbit/app/ui/screens/review/ReviewViewModel.kt
?? app/src/main/java/com/orbit/app/ui/screens/search/
?? app/src/main/java/com/orbit/app/ui/screens/settings/AiSettingsViewModel.kt
?? app/src/main/java/com/orbit/app/ui/screens/settings/LocalDataToolsViewModel.kt
?? app/src/main/java/com/orbit/app/ui/screens/situation/SituationAiViewModel.kt
?? app/src/main/java/com/orbit/app/ui/screens/spaces/SpacesViewModel.kt
?? app/src/main/java/com/orbit/app/ui/time/
?? app/src/main/res/drawable/ic_luma_launcher_foreground.xml
?? app/src/main/res/drawable/ic_luma_notification.xml
?? app/src/main/res/mipmap-anydpi-v26/
?? app/src/main/res/values-v31/
?? app/src/test/java/com/orbit/app/data/local/OrbitTypeConvertersTest.kt
?? app/src/test/java/com/orbit/app/data/repository/
?? app/src/test/java/com/orbit/app/domain/
?? app/src/test/java/com/orbit/app/integrations/
?? app/src/test/java/com/orbit/app/reminders/
?? docs/
?? prompts/
?? publish-debug-apk.ps1
?? scripts/
?? serve-apk.ps1
?? tests/
```

## Empty directories

- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\09_future_ai_full_source\orbit_future_ai_full_codex_package\subpackages`
- `_cleanup_before_luma_v4_upgrade\unknown_review_needed`
- `_cleanup_before_luma_v4_upgrade\v3_agent_stack_backup\.agents`
- `app\src\main\res\mipmap-hdpi`

## Generated/cache directories

- `.gradle`
- `.kotlin`
- `app\build`
- `app\build\generated\ap_generated_sources\debug\out`
- `app\build\generated\ap_generated_sources\release\out`
- `app\build\intermediates\android_test_lint_partial_results\debug\lintAnalyzeDebugAndroidTest\out`
- `app\build\intermediates\compressed_assets\debug\compressDebugAssets\out`
- `app\build\intermediates\compressed_assets\release\compressReleaseAssets\out`
- `app\build\intermediates\data_binding_layout_info_type_merge\debug\mergeDebugResources\out`
- `app\build\intermediates\data_binding_layout_info_type_merge\release\mergeReleaseResources\out`
- `app\build\intermediates\data_binding_layout_info_type_package\debug\packageDebugResources\out`
- `app\build\intermediates\data_binding_layout_info_type_package\release\packageReleaseResources\out`
- `app\build\intermediates\desugar_graph\debug\dexBuilderDebug\out`
- `app\build\intermediates\desugar_graph\release\dexBuilderRelease\out`
- `app\build\intermediates\external_libs_dex_archive\debug\dexBuilderDebug\out`
- `app\build\intermediates\external_libs_dex_archive\release\dexBuilderRelease\out`
- `app\build\intermediates\external_libs_dex_archive_with_artifact_transforms\debug\dexBuilderDebug\out`
- `app\build\intermediates\external_libs_dex_archive_with_artifact_transforms\release\dexBuilderRelease\out`
- `app\build\intermediates\global_synthetics_external_lib\debug\dexBuilderDebug\out`
- `app\build\intermediates\global_synthetics_external_lib\release\dexBuilderRelease\out`
- `app\build\intermediates\global_synthetics_external_libs_artifact_transform\debug\dexBuilderDebug\out`
- `app\build\intermediates\global_synthetics_external_libs_artifact_transform\release\dexBuilderRelease\out`
- `app\build\intermediates\global_synthetics_mixed_scope\debug\dexBuilderDebug\out`
- `app\build\intermediates\global_synthetics_mixed_scope\release\dexBuilderRelease\out`
- `app\build\intermediates\global_synthetics_project\debug\dexBuilderDebug\out`
- `app\build\intermediates\global_synthetics_project\release\dexBuilderRelease\out`
- `app\build\intermediates\global_synthetics_subproject\debug\dexBuilderDebug\out`
- `app\build\intermediates\global_synthetics_subproject\release\dexBuilderRelease\out`
- `app\build\intermediates\java_res\debug\processDebugJavaRes\out`
- `app\build\intermediates\java_res\debugUnitTest\processDebugUnitTestJavaRes\out`
- `app\build\intermediates\java_res\release\processReleaseJavaRes\out`
- `app\build\intermediates\java_res\releaseUnitTest\processReleaseUnitTestJavaRes\out`
- `app\build\intermediates\lint_partial_results\debug\lintAnalyzeDebug\out`
- `app\build\intermediates\lint_vital_partial_results\release\lintVitalAnalyzeRelease\out`
- `app\build\intermediates\merged_jni_libs\debug\mergeDebugJniLibFolders\out`
- `app\build\intermediates\merged_jni_libs\release\mergeReleaseJniLibFolders\out`
- `app\build\intermediates\merged_native_libs\debug\mergeDebugNativeLibs\out`
- `app\build\intermediates\merged_native_libs\release\mergeReleaseNativeLibs\out`
- `app\build\intermediates\merged_res_blame_folder\debug\mergeDebugResources\out`
- `app\build\intermediates\merged_res_blame_folder\release\mergeReleaseResources\out`
- `app\build\intermediates\merged_shaders\debug\mergeDebugShaders\out`
- `app\build\intermediates\merged_shaders\release\mergeReleaseShaders\out`
- `app\build\intermediates\merged_test_only_native_libs\debug\mergeDebugNativeLibs\out`
- `app\build\intermediates\merged_test_only_native_libs\release\mergeReleaseNativeLibs\out`
- `app\build\intermediates\mixed_scope_dex_archive\debug\dexBuilderDebug\out`
- `app\build\intermediates\mixed_scope_dex_archive\release\dexBuilderRelease\out`
- `app\build\intermediates\native_symbol_tables\release\extractReleaseNativeSymbolTables\out`
- `app\build\intermediates\project_dex_archive\debug\dexBuilderDebug\out`
- `app\build\intermediates\project_dex_archive\release\dexBuilderRelease\out`
- `app\build\intermediates\stripped_native_libs\debug\stripDebugDebugSymbols\out`
- `app\build\intermediates\stripped_native_libs\release\stripReleaseDebugSymbols\out`
- `app\build\intermediates\sub_project_dex_archive\debug\dexBuilderDebug\out`
- `app\build\intermediates\sub_project_dex_archive\release\dexBuilderRelease\out`
- `app\build\intermediates\unit_test_lint_partial_results\debug\lintAnalyzeDebugUnitTest\out`

## Backup/log candidates

- `_cleanup_before_luma_v4_upgrade\logs_and_temp\apk-server.err.log`
- `_cleanup_before_luma_v4_upgrade\logs_and_temp\apk-server.log`
- `_cleanup_before_luma_v4_upgrade\logs_and_temp\apk-server.out.log`

## Archive candidates

> Archives may be legitimate fixtures or deliverables. Classification is mandatory.

- `_cleanup_before_luma_v4_upgrade\old_zips\luma_execute_that_cleaned_codex_package.zip`

## Exact duplicate groups

### Group 1
- `.agents\skills\luma-ai-behavior-guardian\SKILL.md`
- `.codex\skills\luma-ai-behavior-guardian\SKILL.md`

### Group 2
- `.agents\skills\luma-ai-behavior-guardian\references\ai_confirmation_matrix.md`
- `.codex\skills\luma-ai-behavior-guardian\references\ai_confirmation_matrix.md`

### Group 3
- `.agents\skills\luma-compose-ui\references\compose_ui_checklist.md`
- `.codex\skills\luma-compose-ui\references\compose_ui_checklist.md`

### Group 4
- `.agents\skills\luma-docs-package-maintainer\SKILL.md`
- `.codex\skills\luma-docs-package-maintainer\SKILL.md`

### Group 5
- `.agents\skills\luma-feature-intake\SKILL.md`
- `.codex\skills\luma-feature-intake\SKILL.md`

### Group 6
- `.agents\skills\luma-reminder-time-guardian\SKILL.md`
- `.codex\skills\luma-reminder-time-guardian\SKILL.md`

### Group 7
- `.agents\skills\luma-reminder-time-guardian\references\reminder_time_cases.md`
- `.codex\skills\luma-reminder-time-guardian\references\reminder_time_cases.md`

### Group 8
- `.agents\skills\luma-skill-governance\SKILL.md`
- `.codex\skills\luma-skill-governance\SKILL.md`

### Group 9
- `.codex\agents\luma_memory_guardian.toml`
- `_cleanup_before_luma_v4_upgrade\v3_agent_stack_backup\.codex\agents\luma_memory_guardian.toml`

### Group 10
- `.codex\agents\luma_regression_reviewer.toml`
- `_cleanup_before_luma_v4_upgrade\v3_agent_stack_backup\.codex\agents\luma_regression_reviewer.toml`

### Group 11
- `.codex\agents\luma_risk_reviewer.toml`
- `_cleanup_before_luma_v4_upgrade\v3_agent_stack_backup\.codex\agents\luma_risk_reviewer.toml`

### Group 12
- `.codex\agents\luma_ux_reviewer.toml`
- `_cleanup_before_luma_v4_upgrade\v3_agent_stack_backup\.codex\agents\luma_ux_reviewer.toml`

### Group 13
- `.codex\skills\luma-autopilot\SKILL.md`
- `_cleanup_before_luma_v4_upgrade\v3_agent_stack_backup\.codex\skills\luma-autopilot\SKILL.md`

### Group 14
- `.codex\skills\luma-self-learning\SKILL.md`
- `_cleanup_before_luma_v4_upgrade\v3_agent_stack_backup\.codex\skills\luma-self-learning\SKILL.md`

### Group 15
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\agents\android_architect_agent.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\agents\android_architect_agent.md`

### Group 16
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\agents\privacy_security_agent.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\agents\privacy_security_agent.md`

### Group 17
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\agents\product_developer_agent.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\agents\product_developer_agent.md`

### Group 18
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\agents\professional_ui_designer_agent.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\agents\professional_ui_designer_agent.md`

### Group 19
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\agents\qa_reviewer_agent.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\agents\qa_reviewer_agent.md`

### Group 20
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\agents\reminders_notifications_agent.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\agents\reminders_notifications_agent.md`

### Group 21
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\prompts\00_foundation_app_shell.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\prompts\00_foundation_app_shell.md`

### Group 22
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\prompts\01_local_data_room.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\prompts\01_local_data_room.md`

### Group 23
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\prompts\02_capture_suggestion_flow.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\prompts\02_capture_suggestion_flow.md`

### Group 24
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\prompts\03_capture_analyzer.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\prompts\03_capture_analyzer.md`

### Group 25
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\prompts\04_actions_notes_tasks_reminders.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\prompts\04_actions_notes_tasks_reminders.md`

### Group 26
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\prompts\05_real_reminders_notifications.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\prompts\05_real_reminders_notifications.md`

### Group 27
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\prompts\06_spaces.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\prompts\06_spaces.md`

### Group 28
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\prompts\07_review.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\prompts\07_review.md`

### Group 29
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\prompts\08_situation_ai.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\prompts\08_situation_ai.md`

### Group 30
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\prompts\09_appearance_settings.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\prompts\09_appearance_settings.md`

### Group 31
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\prompts\10_monday_optional_integration.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\prompts\10_monday_optional_integration.md`

### Group 32
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\prompts\11_product_developer_review.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\prompts\11_product_developer_review.md`

### Group 33
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\prompts\12_ui_designer_review.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\prompts\12_ui_designer_review.md`

### Group 34
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\prompts\13_qa_subagents_review.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\prompts\13_qa_subagents_review.md`

### Group 35
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\codex\prompts\MASTER_BUILD_PLAN.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\codex\prompts\MASTER_BUILD_PLAN.md`

### Group 36
- `_cleanup_before_luma_v4_upgrade\duplicate_agent_packages\skills\orbit-ui\SKILL.md`
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\skills\orbit-ui\SKILL.md`

### Group 37
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\checklists\android_quality_checklist.md`
- `_cleanup_before_luma_v4_upgrade\old_docs\checklists\android_quality_checklist.md`

### Group 38
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\checklists\home_guardrails.md`
- `_cleanup_before_luma_v4_upgrade\old_docs\checklists\home_guardrails.md`

### Group 39
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\checklists\mvp_acceptance_checklist.md`
- `_cleanup_before_luma_v4_upgrade\old_docs\checklists\mvp_acceptance_checklist.md`

### Group 40
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\specs\data_model.md`
- `_cleanup_before_luma_v4_upgrade\old_docs\specs\data_model.md`

### Group 41
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\specs\notification_requirements.md`
- `_cleanup_before_luma_v4_upgrade\old_docs\specs\notification_requirements.md`

### Group 42
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\specs\orbit_product_spec.md`
- `_cleanup_before_luma_v4_upgrade\old_docs\specs\orbit_product_spec.md`

### Group 43
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\specs\orbit_technical_requirements.md`
- `_cleanup_before_luma_v4_upgrade\old_docs\specs\orbit_technical_requirements.md`

### Group 44
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\workflow\agent_workflow.md`
- `_cleanup_before_luma_v4_upgrade\old_docs\workflow\agent_workflow.md`

### Group 45
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\workflow\windows_codex_tutorial.md`
- `_cleanup_before_luma_v4_upgrade\old_docs\workflow\windows_codex_tutorial.md`

### Group 46
- `_cleanup_before_luma_v4_upgrade\v3_agent_stack_backup\docs\codex\LUMA_AUTOPILOT_SYSTEM.md`
- `docs\codex\LUMA_AUTOPILOT_SYSTEM.md`

### Group 47
- `_cleanup_before_luma_v4_upgrade\v3_agent_stack_backup\docs\codex\LUMA_DEBUG_BUILD_DELIVERY.md`
- `docs\codex\LUMA_DEBUG_BUILD_DELIVERY.md`

### Group 48
- `_cleanup_before_luma_v4_upgrade\v3_agent_stack_backup\docs\codex\LUMA_FEATURE_QUESTIONS.md`
- `docs\codex\LUMA_FEATURE_QUESTIONS.md`

### Group 49
- `_cleanup_before_luma_v4_upgrade\v3_agent_stack_backup\docs\codex\LUMA_FINAL_REPORT_TEMPLATE.md`
- `docs\codex\LUMA_FINAL_REPORT_TEMPLATE.md`

### Group 50
- `_cleanup_before_luma_v4_upgrade\v3_agent_stack_backup\docs\codex\LUMA_REQUEST_ROUTING.md`
- `docs\codex\LUMA_REQUEST_ROUTING.md`

### Group 51
- `_cleanup_before_luma_v4_upgrade\v3_agent_stack_backup\docs\codex\LUMA_RISK_POLICY.md`
- `docs\codex\LUMA_RISK_POLICY.md`

### Group 52
- `_cleanup_before_luma_v4_upgrade\v3_agent_stack_backup\docs\codex\LUMA_RULES.md`
- `docs\codex\LUMA_RULES.md`

### Group 53
- `_cleanup_before_luma_v4_upgrade\v3_agent_stack_backup\docs\codex\learning\LUMA_BUG_GRAVEYARD.md`
- `docs\codex\learning\LUMA_BUG_GRAVEYARD.md`

### Group 54
- `_cleanup_before_luma_v4_upgrade\v3_agent_stack_backup\docs\codex\learning\LUMA_DECISIONS.md`
- `docs\codex\learning\LUMA_DECISIONS.md`

### Group 55
- `_cleanup_before_luma_v4_upgrade\v3_agent_stack_backup\docs\codex\learning\LUMA_PATTERN_LIBRARY.md`
- `docs\codex\learning\LUMA_PATTERN_LIBRARY.md`

### Group 56
- `_cleanup_before_luma_v4_upgrade\v3_agent_stack_backup\docs\codex\learning\LUMA_PROMOTION_QUEUE.md`
- `docs\codex\learning\LUMA_PROMOTION_QUEUE.md`

### Group 57
- `_cleanup_before_luma_v4_upgrade\v3_agent_stack_backup\scripts\codex\validate_luma_agent_stack.py`
- `scripts\codex\validate_luma_agent_stack.py`


## Files not hashed because of the configured ceiling

- None

## Large files (5 MiB or more)

- `_cleanup_before_luma_v4_upgrade\logs_and_temp\orbit-debug.apk`: 17.9 MiB; tracked=false

## Unused import candidates

> Heuristic only. Compiler or IDE confirmation is required.

- `app\src\main\java\com\orbit\app\MainActivity.kt:10` `androidx.compose.runtime.getValue`
- `app\src\main\java\com\orbit\app\MainActivity.kt:12` `androidx.compose.runtime.setValue`
- `app\src\main\java\com\orbit\app\ui\navigation\OrbitApp.kt:9` `androidx.compose.runtime.getValue`
- `app\src\main\java\com\orbit\app\ui\navigation\OrbitApp.kt:12` `androidx.compose.runtime.setValue`
- `app\src\main\java\com\orbit\app\ui\screens\home\CaptureSuggestionSheet.kt:32` `androidx.compose.runtime.getValue`
- `app\src\main\java\com\orbit\app\ui\screens\home\CaptureSuggestionSheet.kt:35` `androidx.compose.runtime.setValue`
- `app\src\main\java\com\orbit\app\ui\screens\home\HomeScreen.kt:45` `androidx.compose.runtime.getValue`
- `app\src\main\java\com\orbit\app\ui\screens\item\ItemDetailScreen.kt:48` `androidx.compose.runtime.getValue`
- `app\src\main\java\com\orbit\app\ui\screens\item\ItemDetailScreen.kt:52` `androidx.compose.runtime.setValue`
- `app\src\main\java\com\orbit\app\ui\screens\review\ReminderDetailScreen.kt:24` `androidx.compose.runtime.getValue`
- `app\src\main\java\com\orbit\app\ui\screens\review\ReviewScreen.kt:33` `androidx.compose.runtime.getValue`
- `app\src\main\java\com\orbit\app\ui\screens\review\ReviewScreen.kt:36` `androidx.compose.runtime.setValue`
- `app\src\main\java\com\orbit\app\ui\screens\search\SearchScreen.kt:29` `androidx.compose.runtime.getValue`
- `app\src\main\java\com\orbit\app\ui\screens\settings\SettingsScreen.kt:56` `androidx.compose.runtime.getValue`
- `app\src\main\java\com\orbit\app\ui\screens\settings\SettingsScreen.kt:59` `androidx.compose.runtime.setValue`
- `app\src\main\java\com\orbit\app\ui\screens\spaces\SpacesScreen.kt:63` `androidx.compose.runtime.getValue`
- `app\src\main\java\com\orbit\app\ui\screens\spaces\SpacesScreen.kt:67` `androidx.compose.runtime.setValue`

## Wildcard import candidates

- None

## TODO/FIXME/HACK candidates

- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\01_initial_mvp_active_source\orbit_codex_agent_package\checklists\android_quality_checklist.md:38` - [ ] No obvious TODO pretending feature is done.
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\04_usable_mvp_uiux_source\orbit_usable_mvp_uiux_codex_package\07_REMINDER_RELIABILITY_P0.md:27` 11. If reboot rescheduling exists, verify it. If not, implement a BootReceiver or clearly mark it as TODO only if implementation is not currently possible.
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\04_usable_mvp_uiux_source\orbit_usable_mvp_uiux_codex_package\07_REMINDER_RELIABILITY_P0.md:38` - Reboot behavior is implemented or explicitly documented as a known limitation with TODO.
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\05_final_consolidated_source\orbit_final_consolidated_codex_package\08_REMINDER_RELIABILITY_P0.md:17` - Phone reboot handling is implemented or explicitly marked with TODO and receiver plan.
- `_cleanup_before_luma_v4_upgrade\extracted_packages\codex_packages\luma_execute_that_package\source_packages\extracted\08_gemini_api_source\orbit_gemini_api_codex_package\09_ATTACHMENTS_MULTIMODAL_LATER.md:34` - document TODO only
- `_cleanup_before_luma_v4_upgrade\old_docs\checklists\android_quality_checklist.md:38` - [ ] No obvious TODO pretending feature is done.

## Debug-output candidates

> Logging may be intentional. Verify build type, privacy, and observability requirements.

- None

## Commented-out code candidates

> Heuristic only. Comments may be documentation or examples.

- None

## Required next step

Classify candidates under `LUMA_CLEANUP_POLICY.md`, establish a build/test baseline, and create a content-bound manifest only for reviewed regular files or empty directories.
