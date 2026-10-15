import * as THREE from 'https://esm.sh/three@0.167.1';
import {RoundedBoxGeometry} from 'https://esm.sh/three@0.167.1/examples/jsm/geometries/RoundedBoxGeometry.js';
import {ConvexGeometry} from 'https://esm.sh/three@0.167.1/examples/jsm/geometries/ConvexGeometry.js';
/** Safari/Chrome WebGL die. Values come from the shared server, never from animation. */
const pool=new Set();
function digits(value,heart=false,pips=false,neutral=false){const c=document.createElement('canvas');c.width=c.height=384;
 const x=c.getContext('2d');x.font='bold '+(heart?({1:184,2:143,3:120,4:102,5:94,6:84}[value]||84):180)+'px sans-serif';x.textAlign='center';x.textBaseline='middle';x.shadowColor='#ff1e91';x.shadowBlur=24;
 x.fillStyle='#ff9ccf';x.strokeStyle='#ffe3f3';x.lineWidth=1.5;
 // Before an official D6 roll, all six faces use the SAME heart symbol.
 // Never show fake pips or a made-up result while waiting for a server roll.
 if(neutral){
  x.font='bold 214px Georgia,serif';
  x.fillText('♥',192,198);
  x.strokeText('♥',192,198);
 }else if(!heart)x.fillText(String(value),192,200);
 else{
  const spots=[[192,192],[102,102],[282,282],[282,102],[102,282],[102,192],[282,192]];
  const indices={1:[0],2:[1,2],3:[1,0,2],4:[1,2,3,4],
   5:[1,2,3,4,0],6:[1,2,3,4,5,6]}[value]||[0];
  for(const i of indices){
    if(pips){const [px,py]=spots[i];x.beginPath();x.arc(px,py,20,0,Math.PI*2);x.fill();x.stroke();}
    else{x.fillText('♥',...spots[i]);x.strokeText('♥',...spots[i]);}
   }
 }
 const tex=new THREE.CanvasTexture(c);tex.colorSpace=THREE.SRGBColorSpace;return tex;}
// A genuine convex 35-faced die. The polar dual of a 35-vertex convex hull
// has one planar polygonal face per original vertex (no duplicate side labels).
function d35Geometry(){
 const count=35, goldenAngle=Math.PI*(3-Math.sqrt(5));
 const points=Array.from({length:count},(_,i)=>{
  const y=1-2*(i+.5)/count,ring=Math.sqrt(Math.max(0,1-y*y)),a=i*goldenAngle;
  return new THREE.Vector3(Math.cos(a)*ring,y,Math.sin(a)*ring);
 });
 const hull=new ConvexGeometry(points),position=hull.getAttribute('position');
 const triangles=[];
 for(let i=0;i<position.count;i+=3){
  const a=new THREE.Vector3().fromBufferAttribute(position,i),
   b=new THREE.Vector3().fromBufferAttribute(position,i+1),
   c=new THREE.Vector3().fromBufferAttribute(position,i+2);
  const outward=b.clone().sub(a).cross(c.clone().sub(a)).normalize(),
   center=a.clone().add(b).add(c).multiplyScalar(1/3);
  if(outward.dot(center)<0)outward.negate();
  const dist=outward.dot(a);
  if(dist<.01)throw Error('Invalid D35 hull');
  triangles.push({vertices:[a,b,c],dual:outward.divideScalar(dist)});
 }
 hull.dispose();
 const vertices=[],normals=[],segments=[],sideNormals=new Map(),labelCenters=new Map(),polygons=[];
 for(let i=0;i<count;i++){
  const outward=points[i].clone().normalize();
  const corners=triangles.filter(tri=>
   tri.vertices.some(v=>v.distanceToSquared(points[i])<1e-7))
   .map(tri=>tri.dual.clone());
  if(corners.length<3)throw Error('Incomplete D35 face '+(i+1));
  const center=corners.reduce((a,v)=>a.add(v),new THREE.Vector3())
   .divideScalar(corners.length);
  const u=new THREE.Vector3(0,1,0).cross(outward).normalize();
  if(u.lengthSq()<.1)u.set(1,0,0).cross(outward).normalize();
  const v=outward.clone().cross(u).normalize();
  corners.sort((a,b)=>Math.atan2(a.clone().sub(center).dot(v),a.clone().sub(center).dot(u))-
   Math.atan2(b.clone().sub(center).dot(v),b.clone().sub(center).dot(u)));
  for(let j=0;j<corners.length;j++){
   const a=corners[j],b=corners[(j+1)%corners.length];
   segments.push(...a.toArray(),...b.toArray());
  }
  for(let j=1;j<corners.length-1;j++){
   const a=corners[0],b=corners[j],c=corners[j+1];
   // Reverse the fan if needed so external normal and lighting face out.
   const cross=b.clone().sub(a).cross(c.clone().sub(a));
   const tri=cross.dot(outward)>=0?[a,b,c]:[a,c,b];
   for(const p of tri){vertices.push(...p.toArray());normals.push(...outward.toArray());}
  }
  polygons.push({corners:corners.map(p=>p.clone()),normal:outward.clone(),value:i+1});
  sideNormals.set(i+1,outward);
  labelCenters.set(i+1,center.clone().addScaledVector(outward,.055));
 }
 const geo=new THREE.BufferGeometry();
 geo.setAttribute('position',new THREE.Float32BufferAttribute(vertices,3));
 geo.setAttribute('normal',new THREE.Float32BufferAttribute(normals,3));
 const edges=new THREE.BufferGeometry();
 edges.setAttribute('position',new THREE.Float32BufferAttribute(segments,3));
 return {geo,edges,normals:sideNormals,labelCenters,polygons};
}


/**
 * True rounded polyhedron (convex body ⊕ small sphere), not a wireframe or
 * smooth-shaded fake. The original N planar faces remain flat. Narrow
 * cylindrical edge fillets and spherical vertex caps create a rounded,
 * watertight-looking glossy silhouette with PBR lighting.
 *
 * Each original face is translated to q + bevel * outwardNormal where
 * q=(1-shrink)*point. Adjacent faces connect through normal-arc strips;
 * all normal arcs are completed by round vertex caps.
 * Supabase results / dice-face indexing are entirely unaffected.
 */
function roundedPolyhedron(polygons,{bevel=.105,shrink=.075,steps=6}={}){
 const positions=[],shades=[];
 const vertices=new Map(),edges=new Map();
 const pointKey=p=>p.toArray().map(n=>Math.round(n*10000)).join(',');
 const q=p=>p.clone().multiplyScalar(1-shrink);
 const keyVertices=new Map();
 const insertVertex=p=>{
  const key=pointKey(p);
  if(!keyVertices.has(key))keyVertices.set(key,p.clone());
  return key;
 };
 const arc=(na,nb,t)=>{
  const angle=na.angleTo(nb);
  if(angle<1e-8)return na.clone();
  const a=Math.sin((1-t)*angle)/Math.sin(angle),b=Math.sin(t*angle)/Math.sin(angle);
  return na.clone().multiplyScalar(a).addScaledVector(nb,b).normalize();
 };
 const tri=(a,b,c,na,nb,nc)=>{
  // Keep every patch outward-facing even when face ring winds differently.
  const middle=na.clone().add(nb).add(nc).normalize();
  if(b.clone().sub(a).cross(c.clone().sub(a)).dot(middle)<0){
   [b,c]=[c,b];[nb,nc]=[nc,nb];
  }
  for(const [p,n] of [[a,na],[b,nb],[c,nc]]){
   positions.push(...p.toArray());shades.push(...n.toArray());
  }
 };
 for(const face of polygons){
  const n=face.normal.clone().normalize();
  const corners=face.corners;
  // Planar face interiors are retained so every die still has N genuine faces.
  const center=corners.reduce((acc,p)=>acc.add(p),new THREE.Vector3()).divideScalar(corners.length);
  const middle=q(center).addScaledVector(n,bevel);
  const shifted=corners.map(p=>q(p).addScaledVector(n,bevel));
  for(let j=0;j<shifted.length;j++)tri(middle,shifted[j],shifted[(j+1)%shifted.length],n,n,n);
  for(let j=0;j<corners.length;j++){
   const a=insertVertex(corners[j]),b=insertVertex(corners[(j+1)%corners.length]);
   const sorted=a<b?[a,b]:[b,a],key=sorted.join(':');
   if(!edges.has(key))edges.set(key,{a:keyVertices.get(sorted[0]),b:keyVertices.get(sorted[1]),normals:[]});
   edges.get(key).normals.push(n.clone());
   for(const vertexKey of [a,b]){
    if(!vertices.has(vertexKey))vertices.set(vertexKey,{position:keyVertices.get(vertexKey),normals:[]});
    if(!vertices.get(vertexKey).normals.some(old=>old.distanceToSquared(n)<1e-8))
      vertices.get(vertexKey).normals.push(n.clone());
   }
  }
 }
 // A real swept round fillet around each original polyhedron edge.
 for(const edge of edges.values()){
  if(edge.normals.length!==2)throw Error('Non-manifold die edge');
  const [na,nb]=edge.normals,qa=q(edge.a),qb=q(edge.b);
  for(let j=0;j<steps;j++){
   const n0=arc(na,nb,j/steps),n1=arc(na,nb,(j+1)/steps);
   const a=qa.clone().addScaledVector(n0,bevel);
   const b=qb.clone().addScaledVector(n0,bevel);
   const cc=qb.clone().addScaledVector(n1,bevel);
   const d=qa.clone().addScaledVector(n1,bevel);
   tri(a,b,cc,n0,n0,n1);tri(a,cc,d,n0,n1,n1);
  }
 }
 // Smooth corner caps complete the junction of all round bevels.
 for(const corner of vertices.values()){
  const centerNormal=corner.position.clone().normalize();
  const base=q(corner.position),ns=corner.normals;
  const u=new THREE.Vector3(0,1,0).cross(centerNormal);
  if(u.lengthSq()<.01)u.set(1,0,0).cross(centerNormal);
  u.normalize();
  const v=centerNormal.clone().cross(u).normalize();
  ns.sort((a,b)=>Math.atan2(a.dot(v),a.dot(u))-Math.atan2(b.dot(v),b.dot(u)));
  const apex=base.clone().addScaledVector(centerNormal,bevel);
  for(let i=0;i<ns.length;i++){
   const na=ns[i],nb=ns[(i+1)%ns.length];
   for(let j=0;j<steps;j++){
    const n0=arc(na,nb,j/steps),n1=arc(na,nb,(j+1)/steps);
    tri(apex,base.clone().addScaledVector(n0,bevel),
      base.clone().addScaledVector(n1,bevel),centerNormal,n0,n1);
   }
  }
 }
 const geo=new THREE.BufferGeometry();
 geo.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));
 geo.setAttribute('normal',new THREE.Float32BufferAttribute(shades,3));
 geo.computeBoundingSphere();
 return geo;
}
function twentyPolygons(geo){
 const p=geo.getAttribute('position'),faces=[];
 for(let i=0;i<p.count;i+=3){
  const corners=[0,1,2].map(j=>new THREE.Vector3().fromBufferAttribute(p,i+j));
  const center=corners.reduce((a,v)=>a.add(v),new THREE.Vector3()).divideScalar(3);
  const n=corners[1].clone().sub(corners[0])
    .cross(corners[2].clone().sub(corners[0])).normalize();
  if(n.dot(center)<0)n.negate();
  faces.push({value:i/3+1,corners,normal:n});
 }
 if(faces.length!==20)throw Error('D20 must contain exactly 20 faces');
 return faces;
}

function geometry(sides,pips=false,neutral=false){
 const group=new THREE.Group(),normals=new Map();
 const custom=sides===35?d35Geometry():null;
 const original20=sides===20?new THREE.IcosahedronGeometry(1.16,0):null;
 const polygons=sides===35?custom.polygons:sides===20?twentyPolygons(original20):null;
 const geo=polygons?roundedPolyhedron(polygons,{bevel:sides===20?.13:.095,shrink:sides===20?.095:.07,steps:8}):
  new RoundedBoxGeometry(1.8,1.8,1.8,9,.30);
 original20?.dispose();
 custom?.geo.dispose();
 custom?.edges.dispose();
 const mat=new THREE.MeshPhysicalMaterial({
  color:0x130716,metalness:.13,roughness:.10,
  clearcoat:1,clearcoatRoughness:.035,
  specularIntensity:1.65,ior:1.5,transmission:0,thickness:.8,
  side:THREE.FrontSide,flatShading:false
 });
 group.add(new THREE.Mesh(geo,mat));
 // No wireframe: smooth bevel geometry and PBR specular highlights are the
 // only visible transitions between the real black faces on D6, D20 and D35.
 const layout=sides===6?[[1,[0,0,1]],[6,[0,0,-1]],[2,[0,1,0]],
  [5,[0,-1,0]],[3,[1,0,0]],[4,[-1,0,0]]]:null;
 for(let value=1;value<=sides;value++){
  let normal,depth;
  const polygon=polygons?.[value-1];
  if(polygon){normal=polygon.normal.clone();}
  else if(layout){normal=new THREE.Vector3(...layout.find(row=>row[0]===value)[1]);depth=.91;}
  normals.set(value,normal);
  const plane=new THREE.Mesh(new THREE.PlaneGeometry(sides===35?.27:sides===20?.36:1.64,
   sides===35?.27:sides===20?.36:1.64),
    new THREE.MeshBasicMaterial({map:digits(value,sides===6,pips,neutral),transparent:true,depthWrite:false,side:THREE.DoubleSide}));
  if(polygon){
   const center=polygon.corners.reduce((a,p)=>a.add(p),new THREE.Vector3())
    .divideScalar(polygon.corners.length);
   const shrink=sides===20?.095:.07,bevel=sides===20?.13:.095;
   plane.position.copy(center.multiplyScalar(1-shrink)).addScaledVector(normal,bevel+.013);
  }else plane.position.copy(normal).multiplyScalar(depth);
  plane.quaternion.setFromUnitVectors(new THREE.Vector3(0,0,1),normal);
  group.add(plane);
 }
 return {group,normals};
}
export function dieOn(canvas,sides,value,rolling){
 let renderer;try{renderer=new THREE.WebGLRenderer({canvas,alpha:true,antialias:true});canvas.dataset.webglReady='true';canvas.closest('.home-die-platform, .love3d-stage')?.classList.add('webgl-ready');}catch{return ()=>{};}
 renderer.setPixelRatio(Math.min(devicePixelRatio||1,2));
 renderer.setSize(canvas.clientWidth||124,canvas.clientHeight||124,false);
 renderer.outputColorSpace=THREE.SRGBColorSpace;
 renderer.toneMapping=THREE.ACESFilmicToneMapping;
 renderer.toneMappingExposure=1.6;
 const scene=new THREE.Scene(),camera=new THREE.PerspectiveCamera(42,1,.1,15);camera.position.z=(sides===6?5.25:4.15)/Math.min(1.4,Math.max(.8,Number(canvas.dataset.diceZoom)||1));
 camera.aspect=(canvas.clientWidth||124)/(canvas.clientHeight||124);camera.updateProjectionMatrix();
 scene.add(new THREE.AmbientLight(0xffb7df,1.25));
 const key=new THREE.PointLight(0xff3e99,44,12);key.position.set(-2,3.2,3.3);scene.add(key);
 const rim=new THREE.PointLight(0xff9fcb,22,9);rim.position.set(3,-1,1.9);scene.add(rim);
 const white=new THREE.DirectionalLight(0xfff0fa,2.3);white.position.set(1.3,3,4);scene.add(white);
 const neutral=canvas.dataset.diceUnrolled==='true'&&sides===6;
 const built=geometry(sides,canvas.dataset.diceMarkers==='pips',neutral);scene.add(built.group);
 const target=new THREE.Quaternion().setFromUnitVectors(built.normals.get(Number(value)||1),
  new THREE.Vector3(0,0,1));
 // Face officielle presque directement vers la caméra, léger volume visible.
 const tilt=new THREE.Quaternion().setFromEuler(sides===6?
  new THREE.Euler(.16,-.18,-.02):new THREE.Euler(.075,-.09,0));
 target.premultiply(tilt);
 built.group.quaternion.copy(target);
 let frame=0,drag=false,x=0,y=0,vx=0,vy=0,shown=true,lastFrame=0;
 const reduceMotion=window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches===true;
 const duration=rolling&&!reduceMotion?3100:0,start=performance.now();
 let orientationAtSettle=null,settleStart=.56;
 if(duration){
  const seed=new Uint32Array(3);crypto.getRandomValues(seed);
  built.group.quaternion.setFromEuler(new THREE.Euler(
   ...Array.from(seed,n=>n/4294967296*Math.PI*2)));
  built.group.position.y=0;
 }
 const axis=new THREE.Vector3(.63,.78,.42).normalize();
 function step(now){
  frame=0;if(!shown||document.hidden)return;
  const dt=lastFrame?Math.min(.05,Math.max(0,(now-lastFrame)/1000)):0;
  lastFrame=now;
  const t=duration?Math.min(1,(now-start)/duration):1;
  if(t<1){
   if(t<.56){
    // Impulse → free 3D tumble → angular damping, independent of frame rate.
    const rise=Math.min(1,t/.1);
    const decay=Math.pow(Math.max(0,1-t/.78),1.7);
    const angle=(11.5*rise*decay+.4)*dt;
    axis.set(.63+.12*Math.sin(now*.001),.78,.42+.15*Math.cos(now*.0008)).normalize();
    built.group.quaternion.multiply(new THREE.Quaternion().setFromAxisAngle(axis,angle));
   }else{
    if(!orientationAtSettle){
     settleStart=t;orientationAtSettle=built.group.quaternion.clone();
    }
    const p=Math.min(1,(t-settleStart)/(1-settleStart));
    // Interpolate from actual current orientation, never jump at 77%.
    const eased=1-Math.pow(1-p,3);
    built.group.quaternion.slerpQuaternions(orientationAtSettle,target,eased);
   }
   built.group.position.y=.16*Math.pow(Math.sin(Math.PI*t),2);
  }else if(duration){
   built.group.quaternion.copy(target);built.group.position.y=0;
  }else if(!drag){
   built.group.rotateY(vx);built.group.rotateX(vy);
   const dragFactor=Math.exp(-5*dt);vx*=dragFactor;vy*=dragFactor;
  }
  renderer.render(scene,camera);
  if(t<1||drag||Math.abs(vx)+Math.abs(vy)>.003)request();
 }
 const request=()=>{if(!frame)frame=requestAnimationFrame(step);};
 const down=e=>{
  if(duration&&performance.now()-start<duration)return;
  drag=true;x=e.clientX;y=e.clientY;vx=vy=0;canvas.setPointerCapture(e.pointerId);request();
 };
 const move=e=>{if(!drag)return;const dx=e.clientX-x,dy=e.clientY-y;x=e.clientX;y=e.clientY;
  vx=dx*.013;vy=dy*.013;built.group.rotateY(vx);built.group.rotateX(vy);request();};
 const up=()=>{drag=false;request();};
 canvas.addEventListener('pointerdown',down);canvas.addEventListener('pointermove',move);
 canvas.addEventListener('pointerup',up);canvas.addEventListener('pointercancel',up);
 const obs=new IntersectionObserver(entries=>{const old=shown;
  shown=entries[0]?.isIntersecting!==false;if(shown&&!old){lastFrame=0;request();}
 });
 obs.observe(canvas);
 const onVisible=()=>{if(!document.hidden){lastFrame=0;request();}};
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
