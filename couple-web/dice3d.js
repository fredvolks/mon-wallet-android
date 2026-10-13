import * as THREE from 'https://esm.sh/three@0.167.1';
import {RoundedBoxGeometry} from 'https://esm.sh/three@0.167.1/examples/jsm/geometries/RoundedBoxGeometry.js';
/** Safari/Chrome WebGL die. Values come from the shared server, never from animation. */
const pool=new Set();
function digits(value,heart=false){const c=document.createElement('canvas');c.width=c.height=128;
 const x=c.getContext('2d');x.font=heart?'bold 29px sans-serif':'bold 65px sans-serif';x.textAlign='center';
 x.textBaseline='middle';x.shadowColor='#ff4da4';x.shadowBlur=10;
 x.fillStyle='#ffbcdb';
 if(!heart)x.fillText(String(value),64,66);
 else{
  const spots=[[64,64],[34,34],[94,94],[94,34],[34,94],[64,34],[64,94]];
  const indices={1:[0],2:[1,2],3:[1,0,2],4:[1,2,3,4],
   5:[1,2,3,4,0],6:[1,2,3,4,5,6]}[value]||[0];
  for(const i of indices)x.fillText('♥',...spots[i]);
 }
 const tex=new THREE.CanvasTexture(c);tex.colorSpace=THREE.SRGBColorSpace;return tex;}
function geometry(sides){
 const group=new THREE.Group(),normals=new Map();
 const geo=sides===20?new THREE.IcosahedronGeometry(1.16,0):new RoundedBoxGeometry(1.8,1.8,1.8,5,.17);
 const mat=new THREE.MeshPhysicalMaterial({color:0x261125,metalness:.6,roughness:.17,clearcoat:1});
 group.add(new THREE.Mesh(geo,mat));
 const edges=new THREE.LineSegments(new THREE.EdgesGeometry(geo),
  new THREE.LineBasicMaterial({color:0xf85aa7,transparent:true,opacity:.7}));
 edges.scale.setScalar(1.004);group.add(edges);
 const layout=sides===6?[[1,[0,0,1]],[6,[0,0,-1]],[2,[0,1,0]],
  [5,[0,-1,0]],[3,[1,0,0]],[4,[-1,0,0]]]:null;
 for(let value=1;value<=sides;value++){
  let normal,depth;
  if(layout){normal=new THREE.Vector3(...layout.find(row=>row[0]===value)[1]);depth=.91;}
  else{
   const p=geo.getAttribute('position'),i=(value-1)*3;
   const a=new THREE.Vector3().fromBufferAttribute(p,i),b=new THREE.Vector3().fromBufferAttribute(p,i+1),
    c=new THREE.Vector3().fromBufferAttribute(p,i+2);
   const center=a.clone().add(b).add(c).multiplyScalar(1/3);
   normal=b.sub(a).cross(c.sub(a)).normalize();
   if(normal.dot(center)<0)normal.negate();
   depth=center.length()+.04;
  }
  normals.set(value,normal);
  const plane=new THREE.Mesh(new THREE.PlaneGeometry(sides===20?.36:.60,sides===20?.36:.60),
    new THREE.MeshBasicMaterial({map:digits(value,sides===6),transparent:true,depthWrite:false,side:THREE.DoubleSide}));
  plane.position.copy(normal).multiplyScalar(depth);
  plane.quaternion.setFromUnitVectors(new THREE.Vector3(0,0,1),normal);
  group.add(plane);
 }
 return {group,normals};
}
export function dieOn(canvas,sides,value,rolling){
 let renderer;try{renderer=new THREE.WebGLRenderer({canvas,alpha:true,antialias:true});}catch{return ()=>{};}
 renderer.setSize(canvas.clientWidth||124,canvas.clientHeight||124,false);
 renderer.setPixelRatio(Math.min(devicePixelRatio||1,2));
 const scene=new THREE.Scene(),camera=new THREE.PerspectiveCamera(44,1,.1,15);camera.position.z=4.3;
 scene.add(new THREE.AmbientLight(0xecaada,.9));
 const light=new THREE.PointLight(0xff56aa,30);light.position.set(-1,2,3);scene.add(light);
 const built=geometry(sides);scene.add(built.group);
 const target=new THREE.Quaternion().setFromUnitVectors(built.normals.get(Number(value)||1),
  new THREE.Vector3(0,0,1));
 built.group.quaternion.copy(target);
 let frame=0,drag=false,x=0,y=0,vx=0,vy=0,shown=true;
 const start=performance.now(),duration=rolling?2100:0;
 function step(now){
  frame=0;if(!shown||document.hidden)return;
  const t=duration?Math.min(1,(now-start)/duration):1;
  if(t<1){const f=(1-t)*(1-t);
   built.group.rotateY(.25*f);built.group.rotateX(.20*f);
   if(t>.85)built.group.quaternion.slerp(target,(t-.85)/.15);
  }else if(!drag){built.group.rotateY(vx);built.group.rotateX(vy);vx*=.87;vy*=.87;}
  renderer.render(scene,camera);
  if(t<1||drag||Math.abs(vx)+Math.abs(vy)>.003)request();
 }
 const request=()=>{if(!frame)frame=requestAnimationFrame(step);};
 const down=e=>{drag=true;x=e.clientX;y=e.clientY;vx=vy=0;canvas.setPointerCapture(e.pointerId);request();};
 const move=e=>{if(!drag)return;const dx=e.clientX-x,dy=e.clientY-y;x=e.clientX;y=e.clientY;
  vx=dx*.013;vy=dy*.013;built.group.rotateY(vx);built.group.rotateX(vy);request();};
 const up=()=>{drag=false;request();};
 canvas.addEventListener('pointerdown',down);canvas.addEventListener('pointermove',move);
 canvas.addEventListener('pointerup',up);canvas.addEventListener('pointercancel',up);
 const obs=new IntersectionObserver(a=>{shown=a[0]?.isIntersecting!==false;if(shown)request();});
 obs.observe(canvas);
 const onVisible=()=>{if(!document.hidden)request();};
 document.addEventListener('visibilitychange',onVisible);request();
 const clean=()=>{if(frame)cancelAnimationFrame(frame);obs.disconnect();
  document.removeEventListener('visibilitychange',onVisible);
  canvas.removeEventListener('pointerdown',down);canvas.removeEventListener('pointermove',move);
  canvas.removeEventListener('pointerup',up);canvas.removeEventListener('pointercancel',up);
  built.group.traverse(obj=>{obj.geometry?.dispose();if(obj.material){
    for(const mat of Array.isArray(obj.material)?obj.material:[obj.material]){
     mat.map?.dispose();mat.dispose();
    }
  }});
  renderer.dispose();pool.delete(clean);
 };
 pool.add(clean);return clean;
}
export function clearDice(){for(const d of [...pool])d();}
