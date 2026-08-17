package dev.mintychochip.territory.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.mintychochip.guilds.alliances.Alliance;
import dev.mintychochip.territory.model.BlockPos;
import dev.mintychochip.territory.model.Boundary;
import dev.mintychochip.territory.model.Government;
import dev.mintychochip.guilds.Guild;
import dev.mintychochip.guilds.GuildToggles;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.guilds.GovernanceSource;
import dev.mintychochip.territory.permission.GovernanceRegistry;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class EconomyBridgeMintTaxTest {
  @Test void invalidInputsDoNotInvokeSettlement() {
    var settlement = new CountingSettlement();
    var bridge = bridge();
    assertEquals(TaxOutcome.INVALID_AMOUNT, bridge.reportSaleAsync(UUID.randomUUID(), "world", 1, 1, "carrot", 0, "k", settlement).toCompletableFuture().join().outcome());
    assertEquals(0, settlement.calls);
  }
  @Test void committedTaxUsesGoverningGuild() {
    UUID payer=UUID.randomUUID();
    var settlement=new CountingSettlement();
    var reg=new TerritoryRegistry();
    var t=new Territory("t1","T","world",Boundary.ofPolygon(List.of(new BlockPos(0,0),new BlockPos(10,0),new BlockPos(10,10),new BlockPos(0,10)))).withGovernment(Government.monarchy(payer.toString()));
    t=t.withGovernment(Government.monarchy(payer.toString()));
    reg.register(t);
    var guild=new Guild("g1","G",Government.monarchy(payer.toString()),List.of(payer.toString()),GuildToggles.defaults(),Map.of());
    var source=new GovernanceSource(){ public Optional<Guild> guild(String id){return Optional.of(guild);} public List<Guild> guildsForMember(String id){return List.of(guild);} public Optional<Alliance> allianceContainingGuild(String id){return Optional.empty();} public List<Guild> allGuilds(){return List.of(guild);} public List<Alliance> allAlliances(){return List.of();} };
    var b=new EconomyBridge(reg,new GovernanceRegistry(reg,source),GoodsCatalog.defaultCatalog(),new SimulationTreasury(),false);
    var actual=b.reportSaleAsync(payer,"world",1,1,"carrot",100,"event",settlement).toCompletableFuture().join();
    assertEquals(TaxOutcome.NO_TAX,actual.outcome(), actual.toString());
    assertEquals(0,settlement.calls);
  }
  private static EconomyBridge bridge(){return new EconomyBridge(new TerritoryRegistry(),new GovernanceRegistry(new TerritoryRegistry()),GoodsCatalog.defaultCatalog(),new SimulationTreasury(),false);}
  private static final class CountingSettlement implements AsyncTaxSettlement { int calls; String guild; public CompletionStage<AsyncSettlementResult> settle(UUID p,String g,BigDecimal a,String k){calls++;guild=g;return CompletableFuture.completedFuture(new AsyncSettlementResult(AsyncSettlementResult.Status.COMMITTED,java.util.Optional.empty(),java.util.Optional.empty()));} }
}
