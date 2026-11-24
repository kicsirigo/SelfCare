-- SelfCare Zero-Knowledge Cloud Save table & Row Level Security (RLS)
-- Paste this into Supabase Dashboard -> SQL Editor -> Run
-- Also disable "Confirm email" under Authentication -> Providers -> Email
-- so users can sign up with username + password (no email required).

create table if not exists public.cloud_saves (
    user_id uuid primary key references auth.users(id) on delete cascade,
    payload text not null,
    updated_at bigint not null default (extract(epoch from now()) * 1000)::bigint
);

alter table public.cloud_saves enable row level security;

drop policy if exists "Users can read own cloud save" on public.cloud_saves;
create policy "Users can read own cloud save"
    on public.cloud_saves
    for select
    using (auth.uid() = user_id);

drop policy if exists "Users can insert own cloud save" on public.cloud_saves;
create policy "Users can insert own cloud save"
    on public.cloud_saves
    for insert
    with check (auth.uid() = user_id);

drop policy if exists "Users can update own cloud save" on public.cloud_saves;
create policy "Users can update own cloud save"
    on public.cloud_saves
    for update
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

drop policy if exists "Users can delete own cloud save" on public.cloud_saves;
create policy "Users can delete own cloud save"
    on public.cloud_saves
    for delete
    using (auth.uid() = user_id);
