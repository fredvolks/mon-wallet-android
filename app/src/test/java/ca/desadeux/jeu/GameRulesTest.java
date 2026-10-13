package ca.desadeux.jeu;
import org.junit.Test;
import static org.junit.Assert.*;
import ca.desadeux.jeu.GameLogic.Category;
public class GameRulesTest {
 @Test public void weeklyRollHasExpectedWeightedRange(){
  int[] count=new int[7]; int runs=180000;
  for(int i=0;i<runs;i++){int n=GameLogic.rollWeek();assertTrue(n>=1&&n<=6);count[n]++;}
  double[] expected={0,.50,.25,.12,.08,.04,.01};
  for(int i=1;i<=6;i++) assertEquals("week face "+i,expected[i],(double)count[i]/runs,.015);
 }
 @Test public void surpriseDistributionAndFaceCount(){
  for(Category category:Category.values()){
   int[] counts=new int[3]; int runs=120000;
   for(int i=0;i<runs;i++){counts[GameLogic.choose(category).ordinal()]++;}
   for(Category actual:Category.values()){
    double expectation=actual==category?.8:.1;
    assertEquals(category+" to "+actual,expectation,(double)counts[actual.ordinal()]/runs,.015);
   }
   assertEquals(20,GameData.cards[category.ordinal()].length);
   for(int i=1;i<=20;i++)assertEquals(3,GameData.get(category,i).length);
  }
  for(int i=0;i<100;i++){assertTrue(GameLogic.d6()>=1&&GameLogic.d6()<=6);assertTrue(GameLogic.d20()>=1&&GameLogic.d20()<=20);}
 }

 @Test public void everyPersonGetsUniqueCardsEvenWhenTheyPass(){
  long fredSeen=0L,emilieSeen=0L;
  for(int i=0;i<60;i++){
   ChallengePicker.Pick f=ChallengePicker.draw(Category.COQUIN,fredSeen);
   assertNotNull("Fred draw "+i,f);
   assertFalse(ChallengePicker.seen(fredSeen,f.category,f.face));
   fredSeen=ChallengePicker.mark(fredSeen,f.category,f.face);
   ChallengePicker.Pick e=ChallengePicker.draw(Category.SEXUEL,emilieSeen);
   assertNotNull("Émilie draw "+i,e);
   assertFalse(ChallengePicker.seen(emilieSeen,e.category,e.face));
   emilieSeen=ChallengePicker.mark(emilieSeen,e.category,e.face);
   assertEquals(59-i,ChallengePicker.remaining(fredSeen));
   assertEquals(59-i,ChallengePicker.remaining(emilieSeen));
  }
  assertNull(ChallengePicker.draw(Category.SENSUEL,fredSeen));
  assertNull(ChallengePicker.draw(Category.COQUIN,emilieSeen));
  assertEquals(60,Long.bitCount(fredSeen));
  assertEquals(60,Long.bitCount(emilieSeen));
  // Histories are independent: a card seen by one remains available to the other.
  assertEquals(60,ChallengePicker.remaining(0L));
 }
 @Test public void acceptedAndPassedShareSameExcludedHistory(){
  long mask=0;
  ChallengePicker.Pick first=ChallengePicker.draw(Category.COQUIN,mask);
  mask=ChallengePicker.mark(mask,first.category,first.face); // passed
  ChallengePicker.Pick second=ChallengePicker.draw(Category.COQUIN,mask);
  assertNotNull(second);
  assertFalse(first.category==second.category&&first.face==second.face);
  mask=ChallengePicker.mark(mask,second.category,second.face); // accepted
  assertEquals(58,ChallengePicker.remaining(mask));
 }
 @Test public void categoryOddsRemainWeightedWhenAllAvailable(){
  int[] counts=new int[3];
  int runs=60000;
  for(int i=0;i<runs;i++){
   ChallengePicker.Pick pick=ChallengePicker.draw(Category.SENSUEL,0L);
   counts[pick.category.ordinal()]++;
  }
  assertEquals(.8,(double)counts[0]/runs,.015);
  assertEquals(.1,(double)counts[1]/runs,.015);
  assertEquals(.1,(double)counts[2]/runs,.015);
 }

 @Test public void allSixtyCardsProvideActionAndConcreteExample(){
  java.util.Set<String> seen=new java.util.HashSet<>();
  for(Category category:Category.values()){
   for(int face=1;face<=20;face++){
    String[] card=GameData.get(category,face);
    assertEquals(3,card.length);
    assertTrue(card[0].length()>=4);
    assertTrue(card[1].length()>25);
    assertTrue(card[2].length()>25);
    assertTrue("All challenges must be distinct by category and title",
      seen.add(category.name()+":"+card[0]));
    assertTrue("Example required for "+card[0],card[2].startsWith("Exemple : "));
   }
  }
  assertEquals(60,seen.size());
 }
 @Test public void adventHasTwentyFiveSharedActivitiesWithClearRoles(){
  assertEquals(25,AdventData.days.length);
  int together=0,fred=0,emilie=0;
  for(int day=1;day<=25;day++){
   String[] item=AdventData.days[day-1];
   assertEquals(4,item.length);
   for(String text:item)assertFalse(text.trim().isEmpty());
   assertTrue(item[3].startsWith("Exemple : "));
   if(AdventData.isTogether(day)){
    together++;assertEquals("Fred et Émilie",AdventData.giver(day,false));
    assertEquals(AdventData.instruction(day,false),AdventData.instruction(day,true));
   }else{
    if(AdventData.giver(day,false).equals("Fred"))fred++;else emilie++;
    assertNotEquals(AdventData.giver(day,false),AdventData.giver(day,true));
    assertEquals(AdventData.giver(day,false),AdventData.recipient(day,true));
    assertEquals(AdventData.recipient(day,false),AdventData.giver(day,true));
   }
  }
  assertEquals(9,together);assertEquals(8,fred);assertEquals(8,emilie);
 }
}
