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
   for(int i=1;i<=20;i++)assertEquals(2,GameData.get(category,i).length);
  }
  for(int i=0;i<100;i++){assertTrue(GameLogic.d6()>=1&&GameLogic.d6()<=6);assertTrue(GameLogic.d20()>=1&&GameLogic.d20()<=20);}
 }
}
