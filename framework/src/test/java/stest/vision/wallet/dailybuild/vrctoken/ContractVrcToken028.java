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
public class ContractVrcToken028 {

  private static final long TotalSupply = 10000000L;
  private static ByteString assetAccountId = null;
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  int i1 = randomInt(6666666, 9999999);
  ByteString tokenId1 = ByteString.copyFromUtf8(String.valueOf(i1));
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
  private byte[] transferTokenContractAddress;
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");

  private static int randomInt(int minInt, int maxInt) {
    return (int) Math.round(Math.random() * (maxInt - minInt) + minInt);
  }

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

  @Test(enabled = true, description = "Deploy tokenBalanceWithSameName contract")
  public void deploy01TransferTokenContract() {

    Assert
        .assertTrue(PublicMethed.sendcoin(dev001Address, 4048000000L, fromAddress,
            testKey002, blockingStubFull));
    logger.info(
        "dev001Address:" + Base58.encode58Check(dev001Address));
    Assert
        .assertTrue(PublicMethed.sendcoin(user001Address, 4048000000L, fromAddress,
            testKey002, blockingStubFull));
    logger.info(
        "user001Address:" + Base58.encode58Check(user001Address));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    // freeze balance
    Assert.assertTrue(PublicMethed.freezeBalanceGetEntropy(dev001Address, 204800000,
        3, 1, dev001Key, blockingStubFull));

    Assert.assertTrue(PublicMethed.freezeBalanceGetEntropy(user001Address, 2048000000,
        3, 1, user001Key, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String tokenName = "testAI_" + randomInt(10000, 90000);
    long start = System.currentTimeMillis() + 2000;
    long end = System.currentTimeMillis() + 1000000000;
    //Create a new AssetIssue success.
    Assert.assertTrue(PublicMethed.createAssetIssue(dev001Address, tokenName, TotalSupply, 1,
        100, start, end, 1, description, url, 10000L,
        10000L, 1L, 1L, dev001Key, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    assetAccountId = PublicMethed.queryAccount(dev001Address, blockingStubFull).getAssetIssuedID();
    // deploy transferTokenContract
    int originEntropyLimit = 50000;
    String filePath = "src/test/resources/soliditycode/contractVrcToken028.sol";
    String contractName = "token";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    transferTokenContractAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit,
            1000000000L, 0, originEntropyLimit, assetAccountId.toStringUtf8(),
            100, null, dev001Key, dev001Address,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Assert
        .assertFalse(PublicMethed.sendcoin(transferTokenContractAddress, 1000000000L, fromAddress,
            testKey002, blockingStubFull));

    // devAddress transfer token to userAddress
    Assert.assertFalse(PublicMethed
        .transferAsset(transferTokenContractAddress, assetAccountId.toByteArray(), 100,
            dev001Address,
            dev001Key,
            blockingStubFull));
  }


  @Test(enabled = true, description = "Trigger tokenBalanceWithSameName")
  public void deploy02TransferTokenContract() {
    // user trigger A to transfer token to B
    Account info;
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(dev001Address,
        blockingStubFull);
    info = PublicMethed.queryAccount(dev001Address, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    Long beforePhotonUsed = resourceInfo.getPhotonUsed();
    Long beforeFreePhotonUsed = resourceInfo.getFreePhotonUsed();
    Long beforeAssetIssueDevAddress = PublicMethed
        .getAssetIssueValue(dev001Address, assetAccountId,
            blockingStubFull);
    Long beforeAssetIssueUserAddress = PublicMethed
        .getAssetIssueValue(user001Address, assetAccountId,
            blockingStubFull);

    Long beforeAssetIssueContractAddress = PublicMethed
        .getAssetIssueValue(transferTokenContractAddress,
            assetAccountId,
            blockingStubFull);
    Long beforeBalanceContractAddress = PublicMethed.queryAccount(transferTokenContractAddress,
        blockingStubFull).getBalance();
    Long beforeUserBalance = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforePhotonUsed:" + beforePhotonUsed);
    logger.info("beforeFreePhotonUsed:" + beforeFreePhotonUsed);
    logger.info("beforeAssetIssueCount:" + beforeAssetIssueContractAddress);
    logger.info("beforeAssetIssueDevAddress:" + beforeAssetIssueDevAddress);
    logger.info("beforeAssetIssueUserAddress:" + beforeAssetIssueUserAddress);
    logger.info("beforeBalanceContractAddress:" + beforeBalanceContractAddress);
    logger.info("beforeUserBalance:" + beforeUserBalance);
    String param =
        "\"" + tokenId1
            .toStringUtf8()
            + "\"";

    final String triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
        "tokenBalanceWithSameName(vrcToken)",
        param, false, 0, 1000000000L, "0",
        0, dev001Address, dev001Key,
        blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account infoafter = PublicMethed.queryAccount(dev001Address, blockingStubFull);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(dev001Address,
        blockingStubFull);
    Long afterBalance = infoafter.getBalance();
    Long afterEntropyUsed = resourceInfoafter.getEntropyUsed();
    Long afterAssetIssueDevAddress = PublicMethed
        .getAssetIssueValue(dev001Address, assetAccountId,
            blockingStubFull);
    Long afterPhotonUsed = resourceInfoafter.getPhotonUsed();
    Long afterFreePhotonUsed = resourceInfoafter.getFreePhotonUsed();
    Long afterAssetIssueContractAddress = PublicMethed
        .getAssetIssueValue(transferTokenContractAddress,
            assetAccountId,
            blockingStubFull);
    Long afterAssetIssueUserAddress = PublicMethed
        .getAssetIssueValue(user001Address, assetAccountId,
            blockingStubFull);
    Long afterBalanceContractAddress = PublicMethed.queryAccount(transferTokenContractAddress,
        blockingStubFull).getBalance();
    Long afterUserBalance = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();

    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEntropyUsed:" + afterEntropyUsed);
    logger.info("afterPhotonUsed:" + afterPhotonUsed);
    logger.info("afterFreePhotonUsed:" + afterFreePhotonUsed);
    logger.info("afterAssetIssueCount:" + afterAssetIssueDevAddress);
    logger.info("afterAssetIssueDevAddress:" + afterAssetIssueContractAddress);
    logger.info("afterAssetIssueUserAddress:" + afterAssetIssueUserAddress);
    logger.info("afterBalanceContractAddress:" + afterBalanceContractAddress);
    logger.info("afterUserBalance:" + afterUserBalance);

    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(triggerTxid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);
    Assert.assertTrue(afterAssetIssueUserAddress == beforeAssetIssueUserAddress);
    Assert.assertEquals(afterBalanceContractAddress, beforeBalanceContractAddress);
    Assert.assertTrue(afterAssetIssueContractAddress == beforeAssetIssueContractAddress);

    String triggerTxid1 = PublicMethed.triggerContract(transferTokenContractAddress,
        "getA()",
        "#", false, 0, 1000000000L, "0",
        0, dev001Address, dev001Key,
        blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Optional<TransactionInfo> infoById1 = null;
    infoById1 = PublicMethed.getTransactionInfoById(triggerTxid1, blockingStubFull);
    Long returnnumber1 = ByteArray.toLong(ByteArray
        .fromHexString(ByteArray.toHexString(infoById1.get().getContractResult(0).toByteArray())));
    Assert.assertTrue(returnnumber1 == 9);
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


