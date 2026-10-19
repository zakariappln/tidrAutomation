@ui @backup-restore @out-of-scope
Feature: Backup and restore
  # OUT OF SCOPE (decision 2026-10-18): Backup & Restore is not automated. Kept as specification.
  An administrator configures housekeeping (data retention) and can view available snapshots.
  No menu entry; reached by direct URL.

  # SECURITY DEFECT CONFIRMED LIVE 2026-10-17, not fixed yet: GET restore_snapshot_list returns
  # HTTP 200 with a JSON snapshot list to a caller with NO session (no @login_required on the
  # route, views/backup_and_restore/views.py:76-106). The data is currently hard-coded mock data,
  # but the missing auth gate is real and will leak real backup metadata once wired up. Per the
  # tag policy the scenario asserts the CORRECT behaviour and is @known-defect.
  #
  # Source (views/setting/forms.py:179-189): the housekeeping period choices really include
  # ('100', 'Test') (1 hour) and label '3' as "2 Month"; whether "Test" belongs in production is the
  # open product question.


  @regression @negative @wip
  Scenario: The housekeeping period dropdown contains only production-appropriate options
    Given I am logged in as "admin"
    When I navigate directly to "/en/backup_and_restore/"
    Then the housekeeping period options should not include "Test"

  @regression @negative @security @known-defect @wip
  Scenario: BUG - the restore-snapshot list is not served without authentication
    When I call "/en/backup_and_restore/restore_snapshot_list" without an active session
    Then the anonymous response should be a redirect to the login page
