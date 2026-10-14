// Disposable test-only fixture. Uses real app.js render(), frame(), heroDice()
// and real WebGL dice3d.js; never authenticates or contacts user data.
export function __renderTrueCoupleFixture(tab='home'){
 localStorage.setItem('loveroll-install-prompt-fixture-a','dismissed');
 Object.assign(state,{
   user:{id:'fixture-a',email:'exemple@demo.invalid'},
   profile:{id:'fixture-a',display_name:'Alex',birthday_md:'06-14'},
   wellbeing:{user_id:'fixture-a'},
   guidance:'playful',
   coupleId:'fixture-couple',
   couple:{id:'fixture-couple',owner_user_id:'fixture-a'},
   members:[
    {user_id:'fixture-a',name:'Alex',joined_at:'2026-01-01T00:00:00Z'},
    {user_id:'fixture-b',name:'Camille',joined_at:'2026-01-02T00:00:00Z'}
   ],
   weekly:{id:'fixture-weekly',kind:'weekly',face:4,actor:'fixture-a'},
   monthly:{},session:{id:'fixture-session',self_draws:true,paired_turns:true,starting_user_id:'fixture-a',opened_by:'fixture-a'},
   rollStatus:{both_ready:false,my_rolled:false,partner_rolled:false},
   personal:[],gifts:[],challenges:[],challengesHistory:[],events:[],sessions:[],journal:[],
   advent:[],advent2026Days:[],advent2026Config:null,selectedDay:14,
   occasionSettings:{relationship_started_on:'2018-05-01',anniversary_md:'05-01',celebrants:{}},
   occasionRolls:[],specialEventKeys:[],invitations:[],feedback:[],
   customCards:[],monthlyActivities:[],
   pushPrefs:{enabled:false,weekly:true,invitation:true,monthly:true,special:true},
   showAuth:false,showDiscover:false,inviteAccepted:false,demoMode:false,
   tab
 });
 render();
}
