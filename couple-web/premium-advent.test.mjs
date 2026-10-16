import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {sharedAdventState,adventPremiumHtml,torontoDay} from './premium-advent.mjs';
const file=x=>readFileSync(new URL('./'+x,import.meta.url),'utf8');
const db=file('migrations/20261014_advent_one_door_per_day.sql');
const app=file('app.js');
const couple=[{user_id:'partner-a',name:'Camille Louise'},{user_id:'partner-b',name:'Noémie Marie'}];
const date=x=>new Date(x);
test('Trusted Toronto December boundaries from November to January including timezone at midnight',()=>{
 assert.equal(torontoDay(date('2026-12-01T04:59:00Z')),'2026-11-30');
 assert.equal(torontoDay(date('2026-12-01T05:00:00Z')),'2026-12-01');
 assert.equal(torontoDay(date('2026-12-02T04:59:00Z')),'2026-12-01');
 assert.equal(torontoDay(date('2026-12-02T05:01:00Z')),'2026-12-02');
 assert.equal(sharedAdventState([],date('2026-11-30T18:00:00Z')).days.filter(d=>d.canOpen).length,0);
 const first=sharedAdventState([],date('2026-12-01T15:00:00Z'));
 assert.deepEqual(first.days.filter(d=>d.canOpen).map(d=>d.day),[1]);
 const tenth=sharedAdventState([],date('2026-12-10T15:00:00Z'));
 assert.deepEqual(tenth.days.filter(d=>d.canOpen).map(d=>d.day),[10]);
 assert.equal(tenth.days[0].canOpen,false,'unopened past days cannot be caught up');
 assert.equal(tenth.days[10].status,'locked');
 assert.equal(sharedAdventState([],date('2026-12-26T15:00:00Z')).days.filter(d=>d.canOpen).length,0);
 assert.equal(sharedAdventState([],date('2027-01-01T15:00:00Z')).days.filter(d=>d.canOpen).length,0);
});
test('All 25 boxes in exact 5×5 layout, correct available day without early surprise leak',()=>{
 const html=adventPremiumHtml({when:date('2026-12-05T15:00:00Z'),members:couple});
 assert.equal((html.match(/class="advent-box /g)||[]).length,25);
 assert.equal((html.match(/class="advent-gold-lock"/g)||[]).length,24);
 assert.equal((html.match(/data-action="advent-open:5"/g)||[]).length,1);
 assert.equal((html.match(/class="advent-today-tag"/g)||[]).length,1);
 assert.equal((html.match(/data-action="advent-locked:/g)||[]).length,24);
 assert.match(html,/data-action="advent-locked:25"[^>]*disabled/);
 assert.doesNotMatch(html,/data-action="advent-open:1"/);
 assert.doesNotMatch(html,/class="advent-dialog-shade"/);
});
test('Shared opening persists and displays same official content, author and timer',()=>{
 const record={couple_id:'couple-1',year:2026,day:5,opened_by:'partner-b',
   opened_at:'2026-12-05T15:03:04Z',advent_title:'Un doux moment privé',
   advent_type:'SENSUEL',advent_description:'Dix minutes à deux.',
   advent_timer_minutes:10};
 const html=adventPremiumHtml({when:date('2026-12-05T16:00:00Z'),members:couple,
   openings:[record],selectedDay:5});
 assert.match(html,/advent-box opened/);
 assert.match(html,/data-action="advent-view:5"/);
 assert.doesNotMatch(html,/data-action="advent-open:5"/);
 assert.match(html,/Ouverte par Noémie/);
 assert.match(html,/Un doux moment privé/);
 assert.match(html,/Dix minutes à deux/);
 assert.match(html,/Dix minutes à deux/);
 assert.match(html,/data-action="advent-close"/);
 const partnerView=adventPremiumHtml({when:date('2026-12-05T16:00:00Z'),
   members:[couple[1],couple[0]],openings:[record],selectedDay:5});
 assert.match(partnerView,/Un doux moment privé/);
 assert.match(partnerView,/Ouverte par Noémie/);
});
test('Historical years are read-only and preserve all recorded doors',()=>{
 const last={year:2026,day:25,opened_by:'partner-a',opened_at:'2026-12-25T10:00:00Z',advent_title:'Noël ♥'};
 const data=sharedAdventState([last],date('2027-10-14T15:00:00Z'),2026);
 assert.equal(data.days[24].status,'opened');
 assert.equal(data.days.filter(d=>d.canOpen).length,0);
 const html=adventPremiumHtml({openings:[last],members:couple,
  year:2026,when:date('2027-10-14T15:00:00Z'),showHistory:true});
 assert.match(html,/Décembre 2026/);
 assert.match(html,/Décembre 2027/);
 assert.match(html,/data-action="advent-year:2026"/);
 assert.match(html,/data-action="advent-year:2027"/);
 assert.doesNotMatch(html,/data-action="advent-open:/);
});
test('Surprise content is escaped rather than interpreted as markup',()=>{
 const row={year:2026,day:1,opened_by:'partner-a',
  opened_at:'2026-12-01T15:00:00Z',advent_title:'<img src=x onerror=alert(1)>',
  advent_description:'<script>attack()</script>'};
 const h=adventPremiumHtml({when:date('2026-12-01T16:00:00Z'),members:couple,openings:[row],selectedDay:1});
 assert.doesNotMatch(h,/<img src=x|<script>attack/);
 assert.match(h,/&lt;img src=x/);
 assert.match(h,/&lt;script&gt;attack/);
});
test("Atomic SQL allows only today's exact door, no destructive deletion, uses same couple lock",()=>{
 assert.match(db,/p_day is distinct from extract\(day from d\)::int/);
 assert.match(db,/time zone 'America\/Toronto'/);
 assert.match(db,/perform 1 from public\.couples where id=p_couple for update/);
 assert.match(db,/where couple_id=p_couple and year=extract\(year from d\)::int and day=p_day/);
 assert.match(db,/if found then return rowout/);
 assert.match(db,/returning \* into rowout/);
 assert.match(db,/couple_private\.is_member\(p_couple\)/);
 assert.doesNotMatch(db,/\bdelete\s+from|\btruncate\b|\bdrop\s+table\b/i);
 assert.match(db,/grant execute on function public\.open_advent_day\(uuid,integer\) to authenticated/);
});
test('App uses server date, shared couple realtime, and refreshes only after RPC success',()=>{
 assert.match(app,/rpc\('love_advent_server_date'\)/);
 assert.match(app,/state\.adventServerDate=adventServerDate/);
 assert.match(app,/state\.adventOpening=day;render\(\)/);
 assert.match(app,/await rpc\('open_advent_day'/);
 assert.match(app,/await refreshShared\(\)/);
 assert.match(app,/state\.adventSelected=day/);
 assert.match(app,/'couple_advent','couple_intimacy'/);
 assert.match(app,/if\(verb==='advent-history'\)/);
});
test('CSS includes exact 5 columns × 5 rows, one-screen shell, safe area, glow and reduced motion',()=>{
 const css=file('premium-advent.css');
 assert.match(css,/grid-template-columns:repeat\(5,minmax\(0,1fr\)\)/);
 assert.match(css,/grid-template-rows:repeat\(5,minmax\(0,1fr\)\)/);
 assert.match(css,/100dvh/);
 assert.match(css,/safe-area-inset-top/);
 assert.match(css,/advent-box\.available/);
 assert.match(css,/prefers-reduced-motion:reduce/);
});

test('Missed Advent days never become openable after midnight; visible states are honest',()=>{
 const cal=sharedAdventState([],date('2026-12-10T16:00:00Z'));
 assert.equal(cal.days[0].status,'missed');
 assert.equal(cal.days[0].canOpen,false);
 assert.equal(cal.days[9].status,'available');
 assert.equal(cal.days[10].status,'locked');
 const markup=adventPremiumHtml({when:date('2026-12-10T16:00:00Z'),openings:[]});
 assert.match(markup,/Aujourd’hui/);
 assert.match(markup,/Jour manqué/);
 assert.match(markup,/0 \/ 25/);
 assert.match(markup,/data-action="advent-open:10"/);
 assert.doesNotMatch(markup,/data-action="advent-open:9"/);
});
test('Per-day editor retains server-authenticated import of 25 validated items',()=>{
 assert.match(app,/if\(verb==='advent-editor-day'\)/);
 assert.match(app,/if\(verb==='save-advent-day'\)/);
 assert.match(app,/normalizeLibraryData\(calendar,'advent'\)/);
 assert.match(app,/rpc\('import_love_advent_2026'/);
 assert.match(file('import-private-advent-2026.sql'),/No changes to the current year's previously opened/);
});
