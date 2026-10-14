import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {loginEmail,loginCode,emailAuthMessage} from './email-auth.mjs';
import {authHtml} from './onboarding.mjs';
const app=readFileSync(new URL('./app.js',import.meta.url),'utf8');

test('valid courriel addresses pass verification including underscores',()=>{
 assert.equal(loginEmail('  Fred_VR6@Hotmail.com  '),'fred_vr6@hotmail.com');
 assert.throws(()=>loginEmail('invalid email'),/adresse courriel valide/);
 assert.throws(()=>loginEmail('not@valid'),/adresse courriel valide/);
});
test('valid six-digit email codes can pass, but are never required for confirmation-link emails',()=>{
 assert.equal(loginCode('123456'),'123456');
 assert.throws(()=>loginCode('abcdef'),/6 chiffres/);
 assert.throws(()=>loginCode('12345'),/6 chiffres/);
 assert.match(authHtml(),/Confirm email address/);
 assert.match(authHtml(),/Aucun code à 6 chiffres n’est nécessaire/);
 const html=authHtml(true);
 assert.match(html,/premium-otp-disclosure/);
 assert.match(html,/<summary>J’ai reçu un code à 6 chiffres<\/summary>/);
 assert.match(html,/type="email"/);
 assert.doesNotMatch(html,/Recevoir un courriel de connexion/);
 assert.match(html,/Recevoir mon lien de connexion/);
});
test('rate limits have actionable guidance without retry loops',()=>{
 const warning=emailAuthMessage({message:'email rate limit exceeded',status:429});
 assert.match(warning,/Supabase/);
 assert.match(warning,/N.en redemande pas/);
 assert.match(emailAuthMessage({code:'over_email_send_rate_limit'}),/Limite temporaire/);
 assert.match(emailAuthMessage({message:'invalid otp'}),/lien est expiré ou incorrect/);
 assert.match(app,/loverollEmailSentAt/);
 assert.match(app,/Date\.now\(\)-lastAttempt<60000/);
 assert.match(app,/emailAuthMessage\(error\)/);
});
test('email verification calls the correct validator, not broken over-escaped regex',()=>{
 assert.match(app,/const email=loginEmail\(id\('signin-email'\)\?\.value\)/);
 assert.match(app,/const token=loginCode\(id\('signin-code'\)\?\.value\)/);
 assert.match(app,/client\.auth\.verifyOtp\(\{email,token,type:'email'\}\)/);
 assert.doesNotMatch(app,/!\s*\/\^\[\^@\\\\s\]\+/);
});
