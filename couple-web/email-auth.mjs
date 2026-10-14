// Validation et retours utilisateur pour la connexion par courriel LoveRoll.
// Le fournisseur d'authentification peut envoyer un lien de confirmation
// plutôt qu'un code OTP. Ne jamais promettre un code dans les courriels.
export function loginEmail(value){
  const email=String(value||'').trim().toLowerCase();
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))
    throw new Error('Entre une adresse courriel valide.');
  return email;
}
export function loginCode(value){
  const code=String(value||'').trim();
  if(!/^\d{6}$/.test(code))
    throw new Error('Saisis les 6 chiffres reçus dans ton courriel, si celui-ci contient un code. Sinon, utilise le lien de confirmation.');
  return code;
}
export function emailAuthMessage(error){
  const msg=String(error?.message||'');
  const code=String(error?.code||'');
  const status=Number(error?.status||0);
  if(status===429||code==='over_email_send_rate_limit'||code==='over_request_rate_limit'||
    /rate limit|too many requests|email.*limit/i.test(msg))
    return "Limite temporaire d'envoi de courriels atteinte chez Supabase. N'en redemande pas maintenant. Attends avant de réessayer ou utilise Google/Apple.";
  if(/otp.*expired|token.*expired|invalid.*token|invalid.*otp/i.test(msg))
    return "Ce code ou lien est expiré ou incorrect. Utilise le plus récent courriel reçu; si c'est un courriel de confirmation, clique sur son lien plutôt que d'entrer un code.";
  return 'La connexion par courriel a échoué. Réessaie plus tard ou utilise Google/Apple.';
}
