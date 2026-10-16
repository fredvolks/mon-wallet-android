// Six stable event slots, shared between both partners, without resetting historical rolls.
export function surpriseSlot(kind,owner=null){
 return kind==='birthday'?'birthday:'+String(owner||''):String(kind||'');
}
export function surpriseCustom(settings,kind,owner=null){
 const item=settings?.surprise_event_overrides?.[surpriseSlot(kind,owner)];
 return item&&typeof item==='object'&&!Array.isArray(item)?item:{};
}
export function surpriseTitle(settings,kind,owner,defaultTitle){
 const title=surpriseCustom(settings,kind,owner).title;
 return typeof title==='string'&&title.trim()?title.trim():defaultTitle;
}
export function validAnnualDate(iso){
 if(typeof iso!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(iso))return false;
 const date=new Date(iso+'T12:00:00Z');
 return Number.isFinite(date.getTime())&&date.toISOString().slice(0,10)===iso;
}
