-- Users who accepted a workspace invitation proved mailbox access through the
-- invitation email link, so they should not be asked to verify again.
UPDATE users u
SET email_verified = true
WHERE email_verified = false
  AND EXISTS (
      SELECT 1
      FROM user_invitations ui
      WHERE ui.accepted_at IS NOT NULL
        AND lower(ui.email) = lower(u.email)
  );
