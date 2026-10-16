import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {eveningReviewModal,validEveningReview,eveningCalendarReviewMarkup,REVIEW_QUESTIONS} from './evening-review.mjs';
import {eveningHistoryMarkup} from './evening-calendar.mjs';
const read=f=>readFileSync(new URL(f,import.meta.url),'utf8');
const users=[{user_id:'a',name:'Fred'},{user_id:'b',name:'Émilie'}];
const reviews=[{session_id:'s',author_id:'a',overall:4,connection:5,appreciation:3,again:4,
 comment:'Le massage était super',submitted_at:'2026-10-16T18:00:00Z'},
 {session_id:'s',author_id:'b',overall:5,connection:5,appreciation:4,again:5,
 comment:'J’ai adoré notre soirée',submitted_at:'2026-10-16T19:00:00Z'}];
test('Premium feedback asks four ratings including Appréciation des défis',()=>{
 assert.deepEqual(REVIEW_QUESTIONS.map(x=>x.label),[
 'Rendu global de la soirée','Connexion à deux','Appréciation des défis','Envie de recommencer']);
 const html=eveningReviewModal({sessionId:'s',draft:{overall:4,connection:5,appreciation:3,again:4}});
 assert.equal((html.match(/class="lr-review-question"/g)||[]).length,4);
 assert.equal((html.match(/class="lr-review-heart/g)||[]).length,20);
 assert.match(html,/Enregistrer notre bilan/);
 assert.match(html,/Voir le calendrier/);
 assert.match(html,/maxlength="500"/);
 assert.match(html,/Chacun remplit son ressenti de son côté/);
 assert.doesNotMatch(html,/Plaisir des défis/);
});
test('Selection of hearts and free comments are validated but not overwritten',()=>{
 assert.equal(validEveningReview({overall:4,connection:5,appreciation:3,again:4,comment:'Une belle soirée'}),true);
 assert.equal(validEveningReview({overall:4,connection:0,appreciation:3,again:4}),false);
 assert.equal(validEveningReview({overall:6,connection:5,appreciation:3,again:4}),false);
 assert.equal(validEveningReview({overall:4,connection:5,appreciation:3,again:4,comment:'x'.repeat(501)}),false);
 const html=eveningReviewModal({sessionId:'s',draft:{comment:'<script>test</script>'}});
 assert.doesNotMatch(html,/<script>/);
 assert.match(html,/&lt;script&gt;/);
});
test('The common calendar shows separately authored scores and both free-form notes',()=>{
 const html=eveningCalendarReviewMarkup({sessionId:'s',members:users,reviews,me:'a'});
 assert.match(html,/Fred \(moi\)/);assert.match(html,/Émilie/);
 assert.match(html,/Le massage était super/);
 assert.match(html,/J’ai adoré notre soirée/);
 assert.match(html,/Appréciation des défis/);
 assert.match(html,/data-action="review-open:s"/);
 assert.equal((html.match(/class="evening-review-person"/g)||[]).length,2);
 const none=eveningCalendarReviewMarkup({sessionId:'s',members:users,reviews:reviews.slice(0,1),me:'a'});
 assert.match(none,/En attente de son bilan/);
});
test('Day in calendar includes the independent ratings below preserved challenges',()=>{
 const html=eveningHistoryMarkup({
  sessions:[{id:'s',started_at:'2026-10-16T17:00:00Z',finished_at:'2026-10-16T22:00:00Z'}],
  members:users,stats:[],historyChallenges:[],reviews,me:'a',month:'2026-10',selectedDate:'2026-10-16'
 });
 assert.match(html,/Notre bilan de la soirée/);
 assert.match(html,/Le massage était super/);
 assert.match(html,/Émilie/);
});
test('Backend is individually writable and restricted to the same paired couple',()=>{
 const sql=read('migrations/20261016_evening_hearts_reviews.sql');
 assert.match(sql,/PRIMARY KEY\(session_id,author_id\)/);
 assert.match(sql,/auth\.uid\(\)/);
 assert.match(sql,/couple_private\.is_member\(v_couple\)/);
 assert.match(sql,/couple_private\.is_member\(p_couple\)/);
 assert.match(sql,/REVOKE ALL ON TABLE public\.couple_evening_reviews FROM PUBLIC, anon, authenticated/);
 assert.match(sql,/CHECK\(char_length\(comment\)<=500\)/);
 assert.match(sql,/ON CONFLICT \(session_id,author_id\) DO UPDATE/);
 const app=read('app.js');
 assert.match(app,/rpc\('save_love_evening_review'/);
 assert.match(app,/rpc\('love_evening_reviews_for_couple'/);
 assert.match(app,/state\.reviewModalSessionId=latestEnded\.id/);
 assert.match(app,/rpc\('finish_couple_session',\{p_session:eveningId,p_count:true\}\)/);
 assert.match(app,/if\(board\?\.finished\)/);
 assert.match(app,/if\(parts\[1\]==='completed'\)/);
 const css=read('evening-review.css');
 assert.match(css,/max-height:100%/);
 assert.match(css,/overflow-y:auto/);
});
