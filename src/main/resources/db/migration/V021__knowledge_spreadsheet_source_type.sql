-- Knowledge ingestion now supports uploaded files (PDF, Excel) and scraped
-- URLs in addition to pasted text. 'pdf' and 'url' were already permitted by
-- the original check; this adds 'spreadsheet' for Excel/CSV uploads.

alter table knowledge_sources
    drop constraint knowledge_sources_type_chk;

alter table knowledge_sources
    add constraint knowledge_sources_type_chk
        check (type in ('text', 'pdf', 'spreadsheet', 'url'));
