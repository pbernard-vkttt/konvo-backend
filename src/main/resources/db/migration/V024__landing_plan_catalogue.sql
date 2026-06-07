-- Align the billing catalogue with the public landing-page plans.
-- Enterprise remains contact-us only, so only the four self-serve plans are
-- public/selectable in the app.

alter table plans
    add column monthly_price_ttd numeric(10, 2) not null default 0,
    add column customer_monthly_limit integer not null default 0,
    add column knowledge_chars_limit integer not null default 0;

insert into plans (
    id,
    name,
    monthly_price_usd,
    monthly_price_ttd,
    msg_monthly_limit,
    customer_monthly_limit,
    ai_runs_monthly_limit,
    ai_tokens_monthly_limit,
    knowledge_sources_limit,
    knowledge_chars_limit,
    members_limit,
    is_public
) values
    ('free',     'Free',       0.00,   0.00,   25,   25,    250,   50000,   5,   5000,    1, true),
    ('starter',  'Starter',   44.99, 299.00,  500,  500,   5000,  500000,  25,  25000,   3, true),
    ('growth',   'Growth',   147.99, 999.00, 2000, 2000,  15000, 1500000, 100, 250000,  5, true),
    ('business', 'Business', 324.99, 2199.00, 5000, 5000, 25000, 2500000, 200, 1000000, 15, true)
on conflict (id) do update set
    name = excluded.name,
    monthly_price_usd = excluded.monthly_price_usd,
    monthly_price_ttd = excluded.monthly_price_ttd,
    msg_monthly_limit = excluded.msg_monthly_limit,
    customer_monthly_limit = excluded.customer_monthly_limit,
    ai_runs_monthly_limit = excluded.ai_runs_monthly_limit,
    ai_tokens_monthly_limit = excluded.ai_tokens_monthly_limit,
    knowledge_sources_limit = excluded.knowledge_sources_limit,
    knowledge_chars_limit = excluded.knowledge_chars_limit,
    members_limit = excluded.members_limit,
    is_public = excluded.is_public;

update subscriptions
set plan_id = 'business'
where plan_id = 'pro';

update tenants
set plan = 'business'
where plan = 'pro';

delete from plans
where id = 'pro';
