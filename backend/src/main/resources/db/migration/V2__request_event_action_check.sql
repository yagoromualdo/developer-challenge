-- Espelha o enum HistoryAction: CREATE mais as ações de RequestAction. Ao alterar o enum, crie uma nova migration.
ALTER TABLE request_event
    ADD CONSTRAINT ck_request_event_action CHECK (
        action IN ('CREATE', 'EDIT', 'SUBMIT', 'APPROVE', 'REJECT', 'REQUEST_CHANGES', 'CANCEL'));
