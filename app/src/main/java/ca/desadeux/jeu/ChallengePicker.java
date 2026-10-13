package ca.desadeux.jeu;
import java.security.SecureRandom;
import ca.desadeux.jeu.GameLogic.Category;

/**
 * No challenge repeats for the same recipient in the current evening.
 * A challenge is identified by category + D20 face, i.e. 60 independent cards.
 * Cards enter history as soon as they're drawn (accepted and passed both count).
 */
public final class ChallengePicker {
 private static final SecureRandom RANDOM=new SecureRandom();
 public static final int FACES=20;
 private ChallengePicker(){}
 public static final class Pick {
  public final Category category;
  public final int face;
  Pick(Category category,int face){this.category=category;this.face=face;}
 }
 public static long bit(Category cat,int face){
  if(face<1||face>FACES)throw new IllegalArgumentException("D20 face out of bounds");
  return 1L <<(cat.ordinal()*FACES +face-1);
 }
 public static boolean seen(long mask,Category cat,int face){return (mask & bit(cat,face))!=0;}
 public static long mark(long mask,Category cat,int face){return mask | bit(cat,face);}
 public static int remaining(long mask,Category cat){
  int available=0;
  for(int face=1;face<=FACES;face++)if(!seen(mask,cat,face))available++;
  return available;
 }
 public static int remaining(long mask){
  int n=0;for(Category cat:Category.values())n+=remaining(mask,cat);
  return n;
 }
 public static Pick draw(Category preferred,long seenMask){
  // Weight category 80/10/10 while available. Exhausted categories are
  // removed and the remaining relative weights are normalized.
  int totalWeight=0;
  for(Category cat:Category.values())if(remaining(seenMask,cat)>0)
   totalWeight+=(cat==preferred?80:10);
  if(totalWeight==0)return null;
  int roll=RANDOM.nextInt(totalWeight);
  Category chosen=null;
  for(Category cat:Category.values()){
   if(remaining(seenMask,cat)==0)continue;
   roll-=cat==preferred?80:10;
   if(roll<0){chosen=cat;break;}
  }
  if(chosen==null)throw new IllegalStateException("Category choice impossible");
  int index=RANDOM.nextInt(remaining(seenMask,chosen));
  for(int face=1;face<=FACES;face++){
   if(seen(seenMask,chosen,face))continue;
   if(index--==0)return new Pick(chosen,face);
  }
  throw new IllegalStateException("Face choice impossible");
 }
}
