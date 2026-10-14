import * as THREE from 'https://esm.sh/three@0.167.1';
import {RoundedBoxGeometry} from 'https://esm.sh/three@0.167.1/examples/jsm/geometries/RoundedBoxGeometry.js';
import {ConvexGeometry} from 'https://esm.sh/three@0.167.1/examples/jsm/geometries/ConvexGeometry.js';
/** Safari/Chrome WebGL die. Values come from the shared server, never from animation. */
const pool=new Set();
function digits(value,heart=false){const c=document.createElement('canvas');c.width=c.height=384;
 const x=c.getContext('2d');x.font='bold '+(heart?({1:184,2:143,3:120,4:102,5:94,6:84}[value]||84):180)+'px sans-serif';x.textAlign='center';x.textBaseline='middle';x.shadowColor='#ff1e91';x.shadowBlur=24;
 x.fillStyle='#ff9ccf';x.strokeStyle='#ffe3f3';x.lineWidth=1.5;
 if(!heart)x.fillText(String(value),192,200);
 else{
  const spots=[[192,192],[102,102],[282,282],[282,102],[102,282],[102,192],[282,192]];
  const indices={1:[0],2:[1,2],3:[1,0,2],4:[1,2,3,4],
   5:[1,2,3,4,0],6:[1,2,3,4,5,6]}[value]||[0];
  for(const i of indices){x.fillText('♥',...spots[i]);x.strokeText('♥',...spots[i]);}
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
 const vertices=[],normals=[],segments=[],sideNormals=new Map(),labelCenters=new Map();
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
  sideNormals.set(i+1,outward);
  labelCenters.set(i+1,center.clone().addScaledVector(outward,.055));
 }
 const geo=new THREE.BufferGeometry();
 geo.setAttribute('position',new THREE.Float32BufferAttribute(vertices,3));
 geo.setAttribute('normal',new THREE.Float32BufferAttribute(normals,3));
 const edges=new THREE.BufferGeometry();
 edges.setAttribute('position',new THREE.Float32BufferAttribute(segments,3));
 return {geo,edges,normals:sideNormals,labelCenters};
}

function geometry(sides){
 const group=new THREE.Group(),normals=new Map();
 const custom=sides===35?d35Geometry():null;
 const geo=custom?.geo||(sides===20?new THREE.IcosahedronGeometry(1.16,0):
  new RoundedBoxGeometry(1.8,1.8,1.8,5,.17));
 const mat=new THREE.MeshPhysicalMaterial({
  color:0x130716,metalness:.13,roughness:.10,
  clearcoat:1,clearcoatRoughness:.035,
  specularIntensity:1.65,ior:1.5,transmission:0,thickness:.8,
  side:THREE.FrontSide,flatShading:sides!==6
 });
 group.add(new THREE.Mesh(geo,mat));
 const edges=new THREE.LineSegments(custom?.edges||(sides===6?new THREE.EdgesGeometry(new THREE.BoxGeometry(1.78,1.78,1.78)):new THREE.EdgesGeometry(geo)),
  new THREE.LineBasicMaterial({color:0xf23891,transparent:true,opacity:.68}));
 edges.scale.setScalar(sides===6?.97:1.004);group.add(edges);
 const layout=sides===6?[[1,[0,0,1]],[6,[0,0,-1]],[2,[0,1,0]],
  [5,[0,-1,0]],[3,[1,0,0]],[4,[-1,0,0]]]:null;
 for(let value=1;value<=sides;value++){
  let normal,depth;
  if(custom){normal=custom.normals.get(value);}
  else if(layout){normal=new THREE.Vector3(...layout.find(row=>row[0]===value)[1]);depth=.91;}
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
  const plane=new THREE.Mesh(new THREE.PlaneGeometry(sides===35?.27:sides===20?.36:1.64,
   sides===35?.27:sides===20?.36:1.64),
    new THREE.MeshBasicMaterial({map:digits(value,sides===6),transparent:true,depthWrite:false,side:THREE.DoubleSide}));
  if(custom)plane.position.copy(custom.labelCenters.get(value));
  else plane.position.copy(normal).multiplyScalar(depth);
  plane.quaternion.setFromUnitVectors(new THREE.Vector3(0,0,1),normal);
  group.add(plane);
 }
 return {group,normals};
}
export function dieOn(canvas,sides,value,rolling){
 let renderer;try{renderer=new THREE.WebGLRenderer({canvas,alpha:true,antialias:true});canvas.dataset.webglReady='true';}catch{return ()=>{};}
 renderer.setPixelRatio(Math.min(devicePixelRatio||1,2));
 renderer.setSize(canvas.clientWidth||124,canvas.clientHeight||124,false);
 renderer.outputColorSpace=THREE.SRGBColorSpace;
 renderer.toneMapping=THREE.ACESFilmicToneMapping;
 renderer.toneMappingExposure=1.6;
 const scene=new THREE.Scene(),camera=new THREE.PerspectiveCamera(42,1,.1,15);camera.position.z=sides===6?5.25:4.15;
 camera.aspect=(canvas.clientWidth||124)/(canvas.clientHeight||124);camera.updateProjectionMatrix();
 scene.add(new THREE.AmbientLight(0xffb7df,1.25));
 const key=new THREE.PointLight(0xff3e99,44,12);key.position.set(-2,3.2,3.3);scene.add(key);
 const rim=new THREE.PointLight(0xff9fcb,22,9);rim.position.set(3,-1,1.9);scene.add(rim);
 const white=new THREE.DirectionalLight(0xfff0fa,2.3);white.position.set(1.3,3,4);scene.add(white);
 const built=geometry(sides);scene.add(built.group);
 const target=new THREE.Quaternion().setFromUnitVectors(built.normals.get(Number(value)||1),
  new THREE.Vector3(0,0,1));
 if(sides===6){const tilt=new THREE.Quaternion().setFromEuler(new THREE.Euler(.49,-.52,-.07));target.premultiply(tilt);}
 built.group.quaternion.copy(target);
 let frame=0,drag=false,x=0,y=0,vx=0,vy=0,shown=true;
 const start=performance.now(),duration=rolling?2650:0;
 function step(now){
  frame=0;if(!shown||document.hidden)return;
  const t=duration?Math.min(1,(now-start)/duration):1;
  if(t<1){
   if(t<.77){
    // Rotates in actual 3D space with multiple axes, gradually decreasing
    // angular speed. The backend result never comes from the animation.
    const f=Math.pow(1-t,.32);
    built.group.rotateY(.24*f);
    built.group.rotateX(.21*f);
    built.group.rotateZ(.065*f);
   }else{
    const p=Math.min(1,(t-.77)/.23),smooth=p*p*(3-2*p);
    built.group.quaternion.slerp(target,.12+.88*smooth);
   }
   built.group.position.y=.19*Math.sin(Math.PI*t);
  }else{
   if(duration){built.group.quaternion.copy(target);built.group.position.y=0;}
   if(!drag){built.group.rotateY(vx);built.group.rotateX(vy);vx*=.87;vy*=.87;}
  }
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
