package stest.vision.wallet.fulltest;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.vision.api.GrpcAPI;
import org.vision.api.WalletGrpc;
import org.vision.common.crypto.ECKey;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Utils;
import org.vision.core.Wallet;
import org.vision.protos.Protocol;
import org.vision.protos.Protocol.Account;
import org.vision.protos.Protocol.Block;
import org.vision.protos.contract.AssetIssueContractOuterClass;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter.CommonConstant;
import stest.vision.wallet.common.client.utils.PublicMethed;
import stest.vision.wallet.common.client.utils.TransactionUtils;

@Slf4j
public class ParticipateAssetIssue {

  private static final long now = System.currentTimeMillis();
  private static final long sendAmount = 10250000000L;
  private static String name = "PartAssetIssue_" + Long.toString(now);
  private static long beforeCreateAssetIssueBalance;
  private static long afterCreateAssetIssueBalance;
  private static long afterParticipateAssetIssueBalance;
  private static long start1;
  private static long end1;
  //testng001、testng002、testng003、testng004
  private final String testKey002 =
      "FC8BF0238748587B9617EB6D15D47A66C0E07C1A1959033CF249C6532DC29FE6";
  private final String testKey003 =
      "6815B367FDDE637E53E9ADC8E69424E07724333C9A2B973CFA469975E20753FC";
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
  long totalSupply = now;
  Long freeAssetPhotonLimit = 300000000L;
  Long publicFreeAssetPhotonLimit = 300000000L;
  String description = "f";
  String url = "h";
  //get account
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] createAddress = ecKey1.getAddress();
  String testKeyForCreate = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] participateAssetAddress = ecKey2.getAddress();
  String testKeyForParticipate = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);

  /**
   * constructor.
   */

  public static boolean participateAssetIssue(byte[] to, byte[] assertName, long amount,
      byte[] from, String priKey, WalletGrpc.WalletBlockingStub blockingStubFull) {
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    final ECKey ecKey = temKey;

    AssetIssueContractOuterClass.ParticipateAssetIssueContract.Builder builder =
        AssetIssueContractOuterClass.ParticipateAssetIssueContract
            .newBuilder();
    ByteString bsTo = ByteString.copyFrom(to);
    ByteString bsName = ByteString.copyFrom(assertName);
    ByteString bsOwner = ByteString.copyFrom(from);
    builder.setToAddress(bsTo);
    builder.setAssetName(bsName);
    builder.setOwnerAddress(bsOwner);
    builder.setAmount(amount);
    AssetIssueContractOuterClass.ParticipateAssetIssueContract contract = builder.build();
    Protocol.Transaction transaction = blockingStubFull.participateAssetIssue(contract);
    transaction = signTransaction(ecKey, transaction);
    GrpcAPI.Return response = blockingStubFull.broadcastTransaction(transaction);
    if (response.getResult() == false) {
      return false;
    } else {
      return true;
    }
  }

  /**
   * constructor.
   */

  public static Protocol.Transaction signTransaction(ECKey ecKey,
      Protocol.Transaction transaction) {
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
    if (ecKey == null || ecKey.getPrivKey() == null) {
      //logger.warn("Warning: Can't sign,there is no private key !!");
      return null;
    }
    transaction = TransactionUtils.setTimestamp(transaction);
    return TransactionUtils.sign(transaction, ecKey);
  }

  /**
   * constructor.
   */

  public static boolean transferAsset(byte[] to, byte[] assertName, long amount, byte[] address,
      String priKey, WalletGrpc.WalletBlockingStub blockingStubFull) {
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    final ECKey ecKey = temKey;

    AssetIssueContractOuterClass.TransferAssetContract.Builder builder =
        AssetIssueContractOuterClass.TransferAssetContract
            .newBuilder();
    ByteString bsTo = ByteString.copyFrom(to);
    ByteString bsName = ByteString.copyFrom(assertName);
    ByteString bsOwner = ByteString.copyFrom(address);
    builder.setToAddress(bsTo);
    builder.setAssetName(bsName);
    builder.setOwnerAddress(bsOwner);
    builder.setAmount(amount);

    AssetIssueContractOuterClass.TransferAssetContract contract = builder.build();
    Protocol.Transaction transaction = blockingStubFull.transferAsset(contract);
    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      if (transaction == null) {
        //logger.info("transaction == null");
      } else {
        //logger.info("transaction.getRawData().getContractCount() == 0");
      }
      return false;
    }
    transaction = signTransaction(ecKey, transaction);
    GrpcAPI.Return response = blockingStubFull.broadcastTransaction(transaction);
    if (response.getResult() == false) {
      //logger.info(ByteArray.toStr(response.getMessage().toByteArray()));
      return false;
    } else {
      //Protocol.Account search = queryAccount(ecKey, blockingStubFull);
      return true;
    }
  }

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = false)
  public void beforeClass() {
    logger.info(testKeyForCreate);
    logger.info(testKeyForParticipate);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    //Send coin to 2 account.
    Assert.assertTrue(PublicMethed.sendcoin(createAddress, sendAmount, fromAddress,
        testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethed.sendcoin(participateAssetAddress, sendAmount,
        fromAddress, testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethed.freezeBalance(participateAssetAddress,
        10000000L, 3, testKeyForParticipate, blockingStubFull));
    //Create an asset issue.
    Long start = System.currentTimeMillis() + 2000;
    Long end = System.currentTimeMillis() + 1000000000;
    Assert.assertTrue(PublicMethed.createAssetIssue(createAddress, name, totalSupply, 1, 1,
        start, end, 1, description, url, freeAssetPhotonLimit, publicFreeAssetPhotonLimit,
        10L, 10L, testKeyForCreate, blockingStubFull));
    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    final Account createInfo = PublicMethed.queryAccount(testKeyForCreate, blockingStubFull);
    final Account participateInfo = PublicMethed.queryAccount(testKeyForParticipate,
        blockingStubFull);

    Map<String, Long> assetIssueMap = new HashMap<String, Long>();

    Long temp = 0L;
    assetIssueMap = createInfo.getAssetMap();
    for (String key : assetIssueMap.keySet()) {

      logger.info("Name is " + key);
    }
    for (Long key : assetIssueMap.values()) {
      logger.info("Balance are " + Long.toString(key));
      temp = key;
    }
    beforeCreateAssetIssueBalance = temp;
    start1 = System.currentTimeMillis();
  }

  //@Test(enabled = false)
  @Test(enabled = false, threadPoolSize = 250, invocationCount = 250)
  public void testParticipateAssetIssue() throws InterruptedException {
    Integer i = 0;
    Integer randNum;

    while (i < 20) {
      randNum = i % 4;
      i++;
      fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
          .get(randNum);
      channelFull = ManagedChannelBuilder.forTarget(fullnode)
          .usePlaintext(true)
          .build();
      blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

      participateAssetIssue(createAddress, name.getBytes(),
          1, participateAssetAddress, testKeyForParticipate, blockingStubFull);
    }
  }

  /**
   * constructor.
   */

  @AfterClass(enabled = false)
  public void shutdown() throws InterruptedException {
    //Print the duration.
    end1 = System.currentTimeMillis();
    logger.info("The time is " + Long.toString(end1 - start1));

    Account createInfo = PublicMethed.queryAccount(testKeyForCreate, blockingStubFull);

    Map<String, Long> createAssetIssueMap = new HashMap<String, Long>();

    Long temp = 0L;
    createAssetIssueMap = createInfo.getAssetMap();
    for (String key : createAssetIssueMap.keySet()) {

      logger.info("Name is " + key);
    }
    for (Long key : createAssetIssueMap.values()) {
      logger.info("Balance are " + Long.toString(key));
      temp = key;
    }
    afterCreateAssetIssueBalance = temp;

    temp = 0L;
    Account participateInfo = PublicMethed.queryAccount(testKeyForParticipate, blockingStubFull);
    Map<String, Long> participateAssetIssueMap = new HashMap<String, Long>();
    participateAssetIssueMap = participateInfo.getAssetMap();
    for (Long key : participateAssetIssueMap.values()) {
      logger.info("Balance are " + Long.toString(key));
      temp = key;
    }
    afterParticipateAssetIssueBalance = temp;

    logger.info("Create account has balance " + Long.toString(beforeCreateAssetIssueBalance)
        + " at the beginning");
    logger.info("Create account has balance " + Long.toString(afterCreateAssetIssueBalance)
        + " at the end");
    logger.info("Create account reduce balance " + Long.toString(beforeCreateAssetIssueBalance
        - afterCreateAssetIssueBalance));
    logger.info("Participate account total success transaction is "
        + Long.toString(afterParticipateAssetIssueBalance));

    Integer blockTimes = 0;
    Integer blockTransParticipateNum = 0;

    while (blockTimes < 5) {
      blockTimes++;
      //Print the current block transaction num.
      Block currentBlock = blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build());
      Long currentNum = currentBlock.getBlockHeader().getRawData().getNumber();
      logger.info("The block num " + Long.toString(currentNum)
          + " total transaction is " + Long.toString(currentBlock.getTransactionsCount()));
      try {
        Thread.sleep(3000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    createInfo = PublicMethed.queryAccount(testKeyForCreate, blockingStubFull);
    participateInfo = PublicMethed.queryAccount(testKeyForParticipate, blockingStubFull);
    createAssetIssueMap = new HashMap<String, Long>();
    participateAssetIssueMap = new HashMap<String, Long>();

    temp = 0L;
    createAssetIssueMap = createInfo.getAssetMap();
    for (String key : createAssetIssueMap.keySet()) {

      logger.info("Name is " + key);
    }
    for (Long key : createAssetIssueMap.values()) {
      logger.info("Balance are " + Long.toString(key));
      temp = key;
    }
    afterCreateAssetIssueBalance = temp;

    temp = 0L;
    participateAssetIssueMap = participateInfo.getAssetMap();
    for (Long key : participateAssetIssueMap.values()) {
      logger.info("Balance are " + Long.toString(key));
      temp = key;
    }
    afterParticipateAssetIssueBalance = temp;

    logger.info("Create account has balance " + Long.toString(beforeCreateAssetIssueBalance)
        + "at the beginning");
    logger.info("Create account has balance " + Long.toString(afterCreateAssetIssueBalance)
        + "at the end");
    logger.info("Participate account total success transaction is "
        + Long.toString(afterParticipateAssetIssueBalance));

    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

}


