package stest.vision.wallet.dailybuild.vrctoken;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.vision.api.GrpcAPI.AccountResourceMessage;
import org.vision.api.WalletGrpc;
import org.vision.common.crypto.ECKey;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Utils;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.Account;
import org.vision.protos.Protocol.TransactionInfo;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter;
import stest.vision.wallet.common.client.utils.Base58;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractVrcToken039 {

  private static final long TotalSupply = 10000000L;
  private static final long now = System.currentTimeMillis();
  private static ByteString assetAccountId = null;
  private static String tokenName = "testAssetIssue_" + Long.toString(now);
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  String description = Configuration.getByPath("testng.conf")
      .getString("defaultParameter.assetDescription");
  String url = Configuration.getByPath("testng.conf")
      .getString("defaultParameter.assetUrl");
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] dev001Address = ecKey1.getAddress();
  String dev001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] user001Address = ecKey2.getAddress();
  String user001Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  byte[] proxyTestAddress;
  byte[] atestAddress;
  byte[] btestAddress;
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(Parameter.CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = true)
  public void beforeClass() {

    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

  }


  @Test(enabled = true, description = "Deploy Proxy contract")
  public void deploy01TransferTokenContract() {
    Assert
        .assertTrue(PublicMethed.sendcoin(dev001Address, 4048000000L, fromAddress,
            testKey002, blockingStubFull));
    logger.info(
        "dev001Address:" + Base58.encode58Check(dev001Address));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert
        .assertTrue(PublicMethed.sendcoin(user001Address, 4048000000L, fromAddress,
            testKey002, blockingStubFull));
    logger.info(
        "user001Address:" + Base58.encode58Check(user001Address));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    // freeze balance
    Assert.assertTrue(PublicMethed.freezeBalanceGetEntropy(dev001Address, 204800000,
        0, 1, dev001Key, blockingStubFull));

    Assert.assertTrue(PublicMethed.freezeBalanceGetEntropy(user001Address, 2048000000,
        0, 1, user001Key, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    long start = System.currentTimeMillis() + 2000;
    long end = System.currentTimeMillis() + 1000000000;
    //Create a new AssetIssue success.
    Assert.assertTrue(PublicMethed.createAssetIssue(dev001Address, tokenName, TotalSupply, 1,
        100, start, end, 1, description, url, 10000L,
        10000L, 1L, 1L, dev001Key, blockingStubFull));
    assetAccountId = PublicMethed.queryAccount(dev001Address, blockingStubFull).getAssetIssuedID();
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    // deploy transferTokenContract
    int originEntropyLimit = 50000;

    String filePath = "src/test/resources/soliditycode/contractVrcToken039.sol";
    String contractName = "Proxy";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    proxyTestAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit,
            1000L, 0, originEntropyLimit, assetAccountId.toStringUtf8(),
            1000, null, dev001Key, dev001Address,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String contractName1 = "A";
    HashMap retMap1 = PublicMethed.getBycodeAbi(filePath, contractName1);
    String code1 = retMap1.get("byteCode").toString();
    String abi1 = retMap1.get("abI").toString();
    atestAddress = PublicMethed
        .deployContract(contractName1, abi1, code1, "", maxFeeLimit,
            0L, 0, originEntropyLimit, "0",
            0, null, dev001Key, dev001Address,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String contractName2 = "B";
    HashMap retMap2 = PublicMethed.getBycodeAbi(filePath, contractName2);
    String code2 = retMap2.get("byteCode").toString();
    String abi2 = retMap2.get("abI").toString();
    btestAddress = PublicMethed
        .deployContract(contractName2, abi2, code2, "", maxFeeLimit,
            0L, 0, originEntropyLimit, "0",
            0, null, dev001Key, dev001Address,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    // devAddress transfer token to userAddress

    PublicMethed.waitProduceNextBlock(blockingStubFull);
  }

  @Test(enabled = true, description = "Trigger Proxy contract use AddressA")
  public void deploy02TransferTokenContract() {
    Account info;
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(dev001Address,
        blockingStubFull);
    info = PublicMethed.queryAccount(dev001Address, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    Long beforePhotonUsed = resourceInfo.getPhotonUsed();
    Long beforeFreePhotonUsed = resourceInfo.getFreePhotonUsed();
    Long beforeAssetIssueDevAddress = PublicMethed
        .getAssetIssueValue(dev001Address, assetAccountId, blockingStubFull);
    Long beforeAssetIssueUserAddress = PublicMethed
        .getAssetIssueValue(user001Address, assetAccountId,
            blockingStubFull);

    Long beforeAssetIssueContractAddress = PublicMethed
        .getAssetIssueValue(proxyTestAddress, assetAccountId,
            blockingStubFull);
    Long beforeAssetIssueBAddress = PublicMethed
        .getAssetIssueValue(btestAddress, assetAccountId,
            blockingStubFull);
    Long beforeAssetIssueAAddress = PublicMethed
        .getAssetIssueValue(atestAddress, assetAccountId,
            blockingStubFull);
    Long beforeBalanceContractAddress = PublicMethed.queryAccount(proxyTestAddress,
        blockingStubFull).getBalance();
    Long beforeUserBalance = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforePhotonUsed:" + beforePhotonUsed);
    logger.info("beforeFreePhotonUsed:" + beforeFreePhotonUsed);
    logger.info("beforeAssetIssueContractAddress:" + beforeAssetIssueContractAddress);
    logger.info("beforeAssetIssueBAddress:" + beforeAssetIssueBAddress);

    logger.info("beforeAssetIssueDevAddress:" + beforeAssetIssueDevAddress);
    logger.info("beforeAssetIssueUserAddress:" + beforeAssetIssueUserAddress);
    logger.info("beforeBalanceContractAddress:" + beforeBalanceContractAddress);
    logger.info("beforeUserBalance:" + beforeUserBalance);
    String param =
        "\"" + Base58.encode58Check(atestAddress) + "\"";
    String param1 =
        "\"" + "1" + "\",\"" + Base58.encode58Check(user001Address) + "\",\"" + assetAccountId
            .toStringUtf8()
            + "\"";

    String triggerTxid = PublicMethed.triggerContract(proxyTestAddress,
        "upgradeTo(address)",
        param, false, 0, 1000000000L, "0",
        0, dev001Address, dev001Key,
        blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    final String triggerTxid1 = PublicMethed.triggerContract(proxyTestAddress,
        "trans(uint256,address,vrcToken)",
        param1, false, 0, 1000000000L, assetAccountId
            .toStringUtf8(),
        1, dev001Address, dev001Key,
        blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account infoafter = PublicMethed.queryAccount(dev001Address, blockingStubFull);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(dev001Address,
        blockingStubFull);
    Long afterBalance = infoafter.getBalance();
    Long afterEntropyUsed = resourceInfoafter.getEntropyUsed();
    Long afterAssetIssueDevAddress = PublicMethed
        .getAssetIssueValue(dev001Address, assetAccountId, blockingStubFull);
    Long afterPhotonUsed = resourceInfoafter.getPhotonUsed();
    Long afterFreePhotonUsed = resourceInfoafter.getFreePhotonUsed();
    Long afterAssetIssueContractAddress = PublicMethed
        .getAssetIssueValue(proxyTestAddress, assetAccountId,
            blockingStubFull);
    Long afterAssetIssueBAddress = PublicMethed
        .getAssetIssueValue(btestAddress, assetAccountId,
            blockingStubFull);
    Long afterAssetIssueAAddress = PublicMethed
        .getAssetIssueValue(atestAddress, assetAccountId,
            blockingStubFull);
    Long afterAssetIssueUserAddress = PublicMethed
        .getAssetIssueValue(user001Address, assetAccountId, blockingStubFull);
    Long afterBalanceContractAddress = PublicMethed.queryAccount(proxyTestAddress,
        blockingStubFull).getBalance();
    Long afterUserBalance = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();

    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEntropyUsed:" + afterEntropyUsed);
    logger.info("afterPhotonUsed:" + afterPhotonUsed);
    logger.info("afterFreePhotonUsed:" + afterFreePhotonUsed);
    logger.info("afterAssetIssueCount:" + afterAssetIssueDevAddress);
    logger.info("afterAssetIssueDevAddress:" + afterAssetIssueContractAddress);
    logger.info("afterAssetIssueBAddress:" + afterAssetIssueBAddress);
    logger.info("afterAssetIssueUserAddress:" + afterAssetIssueUserAddress);
    logger.info("afterBalanceContractAddress:" + afterBalanceContractAddress);
    logger.info("afterUserBalance:" + afterUserBalance);

    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(triggerTxid1, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);
    Assert.assertTrue(afterAssetIssueUserAddress == beforeAssetIssueUserAddress);
    Assert.assertTrue(afterBalanceContractAddress == beforeBalanceContractAddress - 1);
    Assert.assertTrue(afterAssetIssueContractAddress == beforeAssetIssueContractAddress + 1);
    Assert.assertTrue(afterAssetIssueDevAddress == beforeAssetIssueDevAddress - 1);
    Assert.assertTrue(afterUserBalance == beforeUserBalance + 1);
    Assert.assertTrue(afterAssetIssueUserAddress == afterAssetIssueUserAddress);
    Assert.assertTrue(afterAssetIssueBAddress == beforeAssetIssueBAddress);
  }

  @Test(enabled = true, description = "Trigger Proxy contract use AddressB")
  public void deploy03TransferTokenContract() {
    Account info1;
    AccountResourceMessage resourceInfo1 = PublicMethed.getAccountResource(dev001Address,
        blockingStubFull);
    info1 = PublicMethed.queryAccount(dev001Address, blockingStubFull);
    Long beforeBalance1 = info1.getBalance();
    Long beforeEntropyUsed1 = resourceInfo1.getEntropyUsed();
    Long beforePhotonUsed1 = resourceInfo1.getPhotonUsed();
    Long beforeFreePhotonUsed1 = resourceInfo1.getFreePhotonUsed();
    Long beforeAssetIssueDevAddress1 = PublicMethed
        .getAssetIssueValue(dev001Address, assetAccountId, blockingStubFull);
    Long beforeAssetIssueUserAddress1 = PublicMethed
        .getAssetIssueValue(user001Address, assetAccountId,
            blockingStubFull);

    Long beforeAssetIssueContractAddress1 = PublicMethed
        .getAssetIssueValue(proxyTestAddress, assetAccountId,
            blockingStubFull);
    Long beforeAssetIssueBAddress1 = PublicMethed
        .getAssetIssueValue(btestAddress, assetAccountId,
            blockingStubFull);

    Long beforeBalanceContractAddress1 = PublicMethed.queryAccount(proxyTestAddress,
        blockingStubFull).getBalance();
    Long beforeUserBalance1 = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();
    logger.info("beforeBalance1:" + beforeBalance1);
    logger.info("beforeEntropyUsed1:" + beforeEntropyUsed1);
    logger.info("beforePhotonUsed1:" + beforePhotonUsed1);
    logger.info("beforeFreePhotonUsed1:" + beforeFreePhotonUsed1);
    logger.info("beforeAssetIssueContractAddress1:" + beforeAssetIssueContractAddress1);
    logger.info("beforeAssetIssueBAddress1:" + beforeAssetIssueBAddress1);

    logger.info("beforeAssetIssueDevAddress1:" + beforeAssetIssueDevAddress1);
    logger.info("beforeAssetIssueUserAddress1:" + beforeAssetIssueUserAddress1);
    logger.info("beforeBalanceContractAddress1:" + beforeBalanceContractAddress1);
    logger.info("beforeUserBalance1:" + beforeUserBalance1);
    String param3 =
        "\"" + Base58.encode58Check(btestAddress) + "\"";
    String param2 =
        "\"" + "1" + "\",\"" + Base58.encode58Check(user001Address) + "\",\"" + assetAccountId
            .toStringUtf8()
            + "\"";

    String triggerTxid2 = PublicMethed.triggerContract(proxyTestAddress,
        "upgradeTo(address)",
        param3, false, 0, 1000000000L, assetAccountId
            .toStringUtf8(),
        1, dev001Address, dev001Key,
        blockingStubFull);
    String triggerTxid3 = PublicMethed.triggerContract(proxyTestAddress,
        "trans(uint256,address,vrcToken)",
        param2, false, 0, 1000000000L, assetAccountId
            .toStringUtf8(),
        1, dev001Address, dev001Key,
        blockingStubFull);
    Account infoafter1 = PublicMethed.queryAccount(dev001Address, blockingStubFull);
    AccountResourceMessage resourceInfoafter1 = PublicMethed.getAccountResource(dev001Address,
        blockingStubFull);
    Long afterBalance1 = infoafter1.getBalance();
    Long afterEntropyUsed1 = resourceInfoafter1.getEntropyUsed();
    Long afterAssetIssueDevAddress1 = PublicMethed
        .getAssetIssueValue(dev001Address, assetAccountId, blockingStubFull);
    Long afterPhotonUsed1 = resourceInfoafter1.getPhotonUsed();
    Long afterFreePhotonUsed1 = resourceInfoafter1.getFreePhotonUsed();
    Long afterAssetIssueContractAddress1 = PublicMethed
        .getAssetIssueValue(proxyTestAddress, assetAccountId,
            blockingStubFull);
    Long afterAssetIssueBAddress1 = PublicMethed
        .getAssetIssueValue(btestAddress, assetAccountId,
            blockingStubFull);

    Long afterAssetIssueUserAddress1 = PublicMethed
        .getAssetIssueValue(user001Address, assetAccountId,
            blockingStubFull);
    Long afterBalanceContractAddress1 = PublicMethed.queryAccount(proxyTestAddress,
        blockingStubFull).getBalance();
    Long afterUserBalance1 = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();

    logger.info("afterBalance1:" + afterBalance1);
    logger.info("afterEntropyUsed1:" + afterEntropyUsed1);
    logger.info("afterPhotonUsed1:" + afterPhotonUsed1);
    logger.info("afterFreePhotonUsed1:" + afterFreePhotonUsed1);
    logger.info("afterAssetIssueCount1:" + afterAssetIssueDevAddress1);
    logger.info("afterAssetIssueDevAddress1:" + afterAssetIssueContractAddress1);
    logger.info("afterAssetIssueBAddress1:" + afterAssetIssueBAddress1);
    logger.info("afterAssetIssueUserAddress1:" + afterAssetIssueUserAddress1);
    logger.info("afterBalanceContractAddress1:" + afterBalanceContractAddress1);
    logger.info("afterUserBalance1:" + afterUserBalance1);

    Optional<TransactionInfo> infoById2 = PublicMethed
        .getTransactionInfoById(triggerTxid3, blockingStubFull);
    Assert.assertTrue(infoById2.get().getResultValue() == 0);
    Assert.assertTrue(afterAssetIssueUserAddress1 == beforeAssetIssueUserAddress1);
    Assert.assertTrue(afterBalanceContractAddress1 == beforeBalanceContractAddress1 - 1);
    Assert.assertTrue(afterAssetIssueContractAddress1 == beforeAssetIssueContractAddress1 + 1);
    Assert.assertTrue(afterAssetIssueDevAddress1 == beforeAssetIssueDevAddress1 - 1);
    Assert.assertTrue(afterUserBalance1 == beforeUserBalance1 + 1);
    Assert.assertTrue(afterAssetIssueUserAddress1 == afterAssetIssueUserAddress1);
    PublicMethed.unFreezeBalance(dev001Address, dev001Key, 1,
        dev001Address, blockingStubFull);
    PublicMethed.unFreezeBalance(user001Address, user001Key, 1,
        user001Address, blockingStubFull);
  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed.freedResource(dev001Address, dev001Key, fromAddress, blockingStubFull);
    PublicMethed.freedResource(user001Address, user001Key, fromAddress, blockingStubFull);
    PublicMethed.unFreezeBalance(fromAddress, testKey002, 0, dev001Address, blockingStubFull);
    PublicMethed.unFreezeBalance(fromAddress, testKey002, 0, user001Address, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }


}


