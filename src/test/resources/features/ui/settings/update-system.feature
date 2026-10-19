@ui @settings @update-system
Feature: System update (patch upload)
  An administrator uploads a .zip patch to update the running ThreatIDR instance.

  # Source facts (docs/threatidr-source-facts.md section 10g, views/home/views.py:455-622):
  #  - the Update System tab is commented out in the settings UI; GET /<lang>/update still renders
  #    home/update.html with the upload form, and the routes are live and login-only
  #  - POST /<lang>/update (multipart "file"): .zip only, <= 7 MB (MAX_CONTENT_LENGTH). Messages:
  #    "No file part", "No selected file", "Invalid file format. Please upload a file in the correct
  #    format.", "File is empty or invalid. Please upload a valid file.", "File uploaded
  #    successfully.", over 7 MB "File size exceeds the 7 MB limit. Please upload a smaller file."
  #  - a Redis lock "patch_upload_lock" (600 s) serialises uploads: "Another upload is in progress".
  #    It is released only on error paths, so a SUCCESSFUL upload blocks uploads for 10 minutes.
  #  - POST /<lang>/apply_update/<name> extracts the zip and runs exactly one .sh with bash.
  #
  # Only rejection paths run in the shared suite: a successful upload holds the lock for 10 minutes
  # and applying a patch executes a shell script on the server.

  Background:
    Given I am logged in as "admin"

  # Needs a vetted test patch zip; a success also holds the upload lock for 10 minutes.
  @regression @positive @blocked-env @wip
  Scenario: Uploading a valid small zip patch is accepted
    When I upload a valid test patch file under 7MB
    Then I should see the upload message "File uploaded successfully."

  # Blocked in this deployment (observed 2026-10-19): every .zip upload ends in "Error saving
  # file." because the view creates its work folder at os.path.abspath('../.') + '/patch_update',
  # i.e. /patch_update in the flask-prod container, which the app user may not write ("[Errno 13]
  # Permission denied: '/patch_update'" in the flask log). Needs a writable patch folder.
  @regression @negative @security @blocked-env
  Scenario: A non-zip file renamed with a .zip extension is rejected
    When I upload a text file renamed to "patch.zip" to the update endpoint
    Then I should see the upload message "File is empty or invalid. Please upload a valid file."

  @regression @negative
  Scenario: A file with another extension is rejected
    When I upload "patch.tar.gz" to the update endpoint
    Then I should see the upload message "Invalid file format. Please upload a file in the correct format."

  # Known defect (observed 2026-10-19): the request never reaches the view's RequestEntityTooLarge
  # handler. CSRFProtect reads the form first, so Flask answers HTTP 413 JSON {"code": 413, "name":
  # "Request Entity Too Large", ...} and no message is flashed. (The page's own JS shows the same
  # text client-side, but only for files picked through its form.) The step sends 8,000,000 bytes:
  # above the app's 7 MiB limit, below nginx's client_max_body_size 8m.
  @regression @negative @known-defect
  Scenario: BUG - a file over 7MB sent to the update endpoint is rejected with a clear message
    When I upload an 8MB zip file to the update endpoint
    Then I should see the upload message "File size exceeds the 7 MB limit. Please upload a smaller file."

  # Needs the upload lock to be held deliberately, which blocks real uploads for 10 minutes.
  @regression @negative @blocked-env @wip
  Scenario: A second upload while one is already in progress is blocked, not interleaved
    Given a patch upload is already in progress
    When I start a second patch upload
    Then I should see the upload message "Another upload is in progress"

  # Needs a container rebuild step outside the UI suite.
  @regression @negative @blocked-env @wip
  Scenario: An applied patch's changes survive a container rebuild
    Given a test patch has been applied
    When the flask-prod container is rebuilt
    Then the patch's changes should still be present

  # <string:zipfile_name> cannot contain "/", so an encoded traversal either misses the route
  # (302 to /<lang>/) or reaches the "No file found to extract" error (views/home/views.py:521-540).
  # Either way the apply must not report success.
  @regression @negative @security @critical
  Scenario: A path-traversal filename in the apply step is rejected
    When I request apply_update for the name "..%2F..%2Fetc%2Fpasswd"
    Then the apply response should not contain "Successfully applied patch."
    And the apply response should not contain "root:"
