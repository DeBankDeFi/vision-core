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
import org.vision.protos.Protocol.TransactionInfo;
import org.vision.protos.contract.SmartContractOuterClass.SmartContract;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractVrcToken073 {

  private static final long now = System.currentTimeMillis();
  private static final long TotalSupply = 1000L;
  private static String tokenName = "testAssetIssue_" + Long.toString(now);
  private static ByteString assetAccountId = null;
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private byte[] transferTokenContractAddress = null;

  private String description = Configuration.getByPath("testng.conf")
      .getString("defaultParameter.assetDescription");
  private String url = Configuration.getByPath("testng.conf")
      .getString("defaultParameter.assetUrl");

  private ECKey ecKey1 = new ECKey(Utils.getRandom());
  private byte[] dev001Address = ecKey1.getAddress();
  private String dev001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

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

    PublicMethed.printAddress(dev001Key);
  }

  @Test(enabled = true, description = "TokenBalance with correct tokenValue and tokenId")
  public void testTokenBalanceContract() {
    Assert.assertTrue(PublicMethed.sendcoin(dev001Address, 11000_000_000L, fromAddress,
        testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress,
        PublicMethed.getFreezeBalanceCount(dev001Address, dev001Key, 300000L,
            blockingStubFull), 0, 1,
        ByteString.copyFrom(dev001Address), testKey002, blockingStubFull));

    Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress, 10_000_000L,
        0, 0, ByteString.copyFrom(dev001Address), testKey002, blockingStubFull));

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    long start = System.currentTimeMillis() + 2000;
    long end = System.currentTimeMillis() + 1000000000;
    //Create a new AssetIssue success.
    Assert.assertTrue(PublicMethed.createAssetIssue(dev001Address, tokenName, TotalSupply, 1,
        10000, start, end, 1, description, url, 100000L, 100000L,
        1L, 1L, dev001Key, blockingStubFull));
    assetAccountId = PublicMethed.queryAccount(dev001Address, blockingStubFull).getAssetIssuedID();
    logger.info("The token name: " + tokenName);
    logger.info("The token ID: " + assetAccountId.toStringUtf8());

    //before deploy, check account resource
    AccountResourceMessage accountResource = PublicMethed.getAccountResource(dev001Address,
        blockingStubFull);
    long entropyLimit = accountResource.getEntropyLimit();
    long entropyUsage = accountResource.getEntropyUsed();
    long balanceBefore = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();
    Long devAssetCountBefore = PublicMethed.getAssetIssueValue(dev001Address,
        assetAccountId, blockingStubFull);

    logger.info("before entropyLimit is " + Long.toString(entropyLimit));
    logger.info("before entropyUsage is " + Long.toString(entropyUsage));
    logger.info("before balanceBefore is " + Long.toString(balanceBefore));
    logger.info("before AssetId: " + assetAccountId.toStringUtf8() + ", devAssetCountBefore: "
        + devAssetCountBefore);

    String filePath = "./src/test/resources/soliditycode/contractVrcToken073.sol";
    String contractName = "Dest";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    String tokenId = assetAccountId.toStringUtf8();
    long tokenValue = 200;
    long callValue = 5;

    String transferTokenTxid = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "", maxFeeLimit,
            callValue, 0, 10000, tokenId, tokenValue,
            null, dev001Key, dev001Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(transferTokenTxid, blockingStubFull);
    logger.info("Deploy entropytotal is " + infoById.get().getReceipt().getEntropyUsageTotal());

    if (transferTokenTxid == null || infoById.get().getResultValue() != 0) {
      Assert.fail("deploy transaction failed with message: " + infoById.get().getResMessage());
    }

    transferTokenContractAddress = infoById.get().getContractAddress().toByteArray();
    SmartContract smartContract = PublicMethed.getContract(transferTokenContractAddress,
        blockingStubFull);
    Assert.assertNotNull(smartContract.getAbi());

    accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
    entropyLimit = accountResource.getEntropyLimit();
    entropyUsage = accountResource.getEntropyUsed();
    long balanceAfter = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();
    Long devAssetCountAfter = PublicMethed.getAssetIssueValue(dev001Address,
        assetAccountId, blockingStubFull);

    logger.info("after entropyLimit is " + Long.toString(entropyLimit));
    logger.info("after entropyUsage is " + Long.toString(entropyUsage));
    logger.info("after balanceAfter is " + Long.toString(balanceAfter));
    logger.info("after AssetId: " + assetAccountId.toStringUtf8() + ", devAssetCountAfter: "
        + devAssetCountAfter);

    Assert.assertFalse(PublicMethed.transferAsset(transferTokenContractAddress,
        assetAccountId.toByteArray(), 100L, dev001Address, dev001Key, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Long contractAssetCount = PublicMethed.getAssetIssueValue(transferTokenContractAddress,
        assetAccountId, blockingStubFull);
    logger.info("Contract has AssetId: " + assetAccountId.toStringUtf8() + ", Count: "
        + contractAssetCount);

    Assert.assertEquals(Long.valueOf(tokenValue),
        Long.valueOf(devAssetCountBefore - devAssetCountAfter));
    Assert.assertEquals(Long.valueOf(tokenValue), contractAssetCount);

    // get and verify the msg.value and msg.id
    Long transferAssetBefore = PublicMethed.getAssetIssueValue(transferTokenContractAddress,
        assetAccountId, blockingStubFull);
    logger.info("before trigger, transferTokenContractAddress has AssetId "
        + assetAccountId.toStringUtf8() + ", Count is " + transferAssetBefore);

    Long devAssetBefore = PublicMethed.getAssetIssueValue(dev001Address,
        assetAccountId, blockingStubFull);
    logger.info("before trigger, dev001Address has AssetId "
        + assetAccountId.toStringUtf8() + ", Count is " + devAssetBefore);

    tokenId = assetAccountId.toStringUtf8();
    String triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
        "getToken(vrcToken)", tokenId, false, 0,
        1000000000L, "0", 0, dev001Address, dev001Key,
        blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed
        .getTransactionInfoById(triggerTxid, blockingStubFull);
    logger.info("Trigger entropytotal is " + infoById.get().getReceipt().getEntropyUsageTotal());

    if (triggerTxid == null || infoById.get().getResultValue() != 0) {
      Assert.fail("transaction failed with message: " + infoById.get().getResMessage());
    }

    accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
    long devEntropyLimitAfter = accountResource.getEntropyLimit();
    long devEntropyUsageAfter = accountResource.getEntropyUsed();
    long devBalanceAfter = PublicMethed.queryAccount(dev001Address, blockingStubFull).getBalance();

    logger.info("after trigger, devEntropyLimitAfter is " + Long.toString(devEntropyLimitAfter));
    logger.info("after trigger, devEntropyUsageAfter is " + Long.toString(devEntropyUsageAfter));
    logger.info("after trigger, devBalanceAfter is " + Long.toString(devBalanceAfter));

    logger.info("The msg value: " + infoById.get().getLogList().get(0).getTopicsList());

    Long msgTokenBalance = ByteArray
        .toLong(infoById.get().getLogList().get(0).getTopicsList().get(1).toByteArray());
    Long msgId = ByteArray
        .toLong(infoById.get().getLogList().get(0).getTopicsList().get(2).toByteArray());
    Long msgTokenValue = ByteArray
        .toLong(infoById.get().getLogList().get(0).getTopicsList().get(3).toByteArray());

    logger.info("msgTokenBalance: " + msgTokenBalance);
    logger.info("msgId: " + msgId);
    logger.info("msgTokenValue: " + msgTokenValue);

    Assert.assertEquals(msgTokenBalance, devAssetBefore);

    tokenId = Long.toString(Long.valueOf(assetAccountId.toStringUtf8()) + 1000);
    triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
        "getToken(vrcToken)", tokenId, false, 0,
        1000000000L, "0", 0, dev001Address, dev001Key,
        blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    infoById = PublicMethed
        .getTransactionInfoById(triggerTxid, blockingStubFull);
    logger.info("Trigger entropytotal is " + infoById.get().getReceipt().getEntropyUsageTotal());

    if (triggerTxid == null || infoById.get().getResultValue() != 0) {
      Assert.fail("transaction failed with message: " + infoById.get().getResMessage());
    }

    logger.info("The msg value: " + infoById.get().getLogList().get(0).getTopicsList());

    msgTokenBalance = ByteArray
        .toLong(infoById.get().getLogList().get(0).getTopicsList().get(1).toByteArray());
    msgId = ByteArray
        .toLong(infoById.get().getLogList().get(0).getTopicsList().get(2).toByteArray());
    msgTokenValue = ByteArray
        .toLong(infoById.get().getLogList().get(0).getTopicsList().get(3).toByteArray());

    logger.info("msgTokenBalance: " + msgTokenBalance);
    logger.info("msgId: " + msgId);
    logger.info("msgTokenValue: " + msgTokenValue);

    Assert.assertEquals(Long.valueOf(0), msgTokenBalance);

    tokenId = Long.toString(Long.MAX_VALUE);

    triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
        "getToken(vrcToken)", tokenId, false, 0,
        1000000000L, "0", 0, dev001Address, dev001Key,
        blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    infoById = PublicMethed
        .getTransactionInfoById(triggerTxid, blockingStubFull);
    logger.info("Trigger entropytotal is " + infoById.get().getReceipt().getEntropyUsageTotal());

    if (triggerTxid == null || infoById.get().getResultValue() != 0) {
      Assert.fail("transaction failed with message: " + infoById.get().getResMessage());
    }

    logger.info("The msg value: " + infoById.get().getLogList().get(0).getTopicsList());

    msgTokenBalance = ByteArray
        .toLong(infoById.get().getLogList().get(0).getTopicsList().get(1).toByteArray());
    msgId = ByteArray
        .toLong(infoById.get().getLogList().get(0).getTopicsList().get(2).toByteArray());
    msgTokenValue = ByteArray
        .toLong(infoById.get().getLogList().get(0).getTopicsList().get(3).toByteArray());

    logger.info("msgTokenBalance: " + msgTokenBalance);
    logger.info("msgId: " + msgId);
    logger.info("msgTokenValue: " + msgTokenValue);

    Assert.assertEquals(Long.valueOf(0), msgTokenBalance);
    // unfreeze resource
    PublicMethed.unFreezeBalance(fromAddress, testKey002, 1,
        dev001Address, blockingStubFull);
    PublicMethed.unFreezeBalance(fromAddress, testKey002, 0,
        dev001Address, blockingStubFull);

  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed.freedResource(dev001Address, dev001Key, fromAddress, blockingStubFull);
    PublicMethed.unFreezeBalance(fromAddress, testKey002, 0, dev001Address, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


