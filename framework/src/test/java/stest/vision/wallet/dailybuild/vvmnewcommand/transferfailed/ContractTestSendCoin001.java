package stest.vision.wallet.dailybuild.vvmnewcommand.transferfailed;

import static org.hamcrest.core.StringContains.containsString;
import static org.vision.api.GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR;
import static org.vision.protos.Protocol.Transaction.Result.contractResult.SUCCESS_VALUE;

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
import org.vision.api.GrpcAPI.Return;
import org.vision.api.GrpcAPI.TransactionExtention;
import org.vision.api.WalletGrpc;
import org.vision.common.crypto.ECKey;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Utils;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.Account;
import org.vision.protos.Protocol.Transaction;
import org.vision.protos.Protocol.Transaction.Result.contractResult;
import org.vision.protos.Protocol.TransactionInfo;
import org.vision.protos.contract.SmartContractOuterClass.SmartContract;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter.CommonConstant;
import stest.vision.wallet.common.client.WalletClient;
import stest.vision.wallet.common.client.utils.Base58;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractTestSendCoin001 {

  private static final long now = System.currentTimeMillis();
  private static final long TotalSupply = 1000L;
  private static String tokenName = "testAssetIssue_" + Long.toString(now);
  private static ByteString assetAccountId = null;
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);
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

  private ECKey ecKey2 = new ECKey(Utils.getRandom());
  private byte[] user001Address = ecKey2.getAddress();
  private String user001Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */
  @BeforeClass(enabled = true)
  public void beforeClass() {

    channelFull = ManagedChannelBuilder.forTarget(fullnode).usePlaintext(true).build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    PublicMethed.printAddress(dev001Key);
  }

  @Test(enabled = true, description = "Sendcoin and transferAsset to contractAddresss ,"
      + "then selfdestruct")
  public void testSendCoinAndTransferAssetContract001() {
    Assert.assertTrue(PublicMethed
        .sendcoin(dev001Address, 3100_000_000L, fromAddress, testKey002, blockingStubFull));

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress,
        PublicMethed.getFreezeBalanceCount(dev001Address, dev001Key, 130000L, blockingStubFull), 0,
        1, ByteString.copyFrom(dev001Address), testKey002, blockingStubFull));

    Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress, 10_000_000L, 0, 0,
        ByteString.copyFrom(dev001Address), testKey002, blockingStubFull));

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    long start = System.currentTimeMillis() + 2000;
    long end = System.currentTimeMillis() + 1000000000;

    //Create a new AssetIssue success.
    Assert.assertTrue(PublicMethed
        .createAssetIssue(dev001Address, tokenName, TotalSupply, 1, 10000, start, end, 1,
            description, url, 100000L, 100000L, 1L, 1L, dev001Key, blockingStubFull));

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    assetAccountId = PublicMethed.queryAccount(dev001Address, blockingStubFull).getAssetIssuedID();

    logger.info("The token name: " + tokenName);
    logger.info("The token ID: " + assetAccountId.toStringUtf8());

    //before deploy, check account resource
    AccountResourceMessage accountResource = PublicMethed
        .getAccountResource(dev001Address, blockingStubFull);
    long entropyLimit = accountResource.getEntropyLimit();
    long entropyUsage = accountResource.getEntropyUsed();
    long balanceBefore = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();
    Long devAssetCountBefore = PublicMethed
        .getAssetIssueValue(dev001Address, assetAccountId, blockingStubFull);

    logger.info("before entropyLimit is " + Long.toString(entropyLimit));
    logger.info("before entropyUsage is " + Long.toString(entropyUsage));
    logger.info("before balanceBefore is " + Long.toString(balanceBefore));
    logger.info("before AssetId: " + assetAccountId.toStringUtf8() + ", devAssetCountBefore: "
        + devAssetCountBefore);

    String filePath = "src/test/resources/soliditycode/contractVrcToken031.sol";
    String contractName = "token";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    String tokenId = assetAccountId.toStringUtf8();
    long tokenValue = 100;
    long callValue = 5;

    final String deployContractTxid = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "", maxFeeLimit,
            callValue, 0, 10000, tokenId, tokenValue, null, dev001Key, dev001Address,
            blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
    entropyLimit = accountResource.getEntropyLimit();
    entropyUsage = accountResource.getEntropyUsed();
    long balanceAfter = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();
    Long devAssetCountAfter = PublicMethed
        .getAssetIssueValue(dev001Address, assetAccountId, blockingStubFull);

    logger.info("after entropyLimit is " + Long.toString(entropyLimit));
    logger.info("after entropyUsage is " + Long.toString(entropyUsage));
    logger.info("after balanceAfter is " + Long.toString(balanceAfter));
    logger.info("after AssetId: " + assetAccountId.toStringUtf8() + ", devAssetCountAfter: "
        + devAssetCountAfter);

    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(deployContractTxid, blockingStubFull);
    logger.info("Deploy entropytotal is " + infoById.get().getReceipt().getEntropyUsageTotal());

    if (deployContractTxid == null || infoById.get().getResultValue() != 0) {
      Assert.fail("deploy transaction failed with message: " + infoById.get().getResMessage()
          .toStringUtf8());
    }

    transferTokenContractAddress = infoById.get().getContractAddress().toByteArray();
    SmartContract smartContract = PublicMethed
        .getContract(transferTokenContractAddress, blockingStubFull);
    Assert.assertNotNull(smartContract.getAbi());

    Return ret = PublicMethed
        .transferAssetForReturn(transferTokenContractAddress, assetAccountId.toByteArray(), 100L,
            dev001Address, dev001Key, blockingStubFull);

    Assert.assertEquals(CONTRACT_VALIDATE_ERROR, ret.getCode());
    Assert.assertEquals("contract validate error : Cannot transfer asset to smartContract.",
        ret.getMessage().toStringUtf8());
    Long contractAssetCount = PublicMethed
        .getAssetIssueValue(transferTokenContractAddress, assetAccountId, blockingStubFull);
    logger.info("Contract has AssetId: " + assetAccountId.toStringUtf8() + ", Count: "
        + contractAssetCount);

    Assert.assertEquals(Long.valueOf(tokenValue), contractAssetCount);

    Return ret1 = PublicMethed
        .sendcoinForReturn(transferTokenContractAddress, 1_000_000L, fromAddress, testKey002,
            blockingStubFull);
    Assert.assertEquals(CONTRACT_VALIDATE_ERROR, ret1.getCode());
    Assert.assertEquals("contract validate error : Cannot transfer VS to a smartContract.",
        ret1.getMessage().toStringUtf8());

    String num = "\"" + Base58.encode58Check(dev001Address) + "\"";

    String txid = PublicMethed
        .triggerContract(transferTokenContractAddress, "kill(address)", num, false, 0, maxFeeLimit,
            dev001Address, dev001Key, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Long contractAssetCountBefore = PublicMethed
        .getAssetIssueValue(transferTokenContractAddress, assetAccountId, blockingStubFull);
    long contractBefore = PublicMethed.queryAccount(transferTokenContractAddress, blockingStubFull)
        .getBalance();

    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);

    Assert.assertTrue(PublicMethed
        .transferAsset(transferTokenContractAddress, assetAccountId.toByteArray(), 100L,
            dev001Address, dev001Key, blockingStubFull));

    Assert.assertTrue(PublicMethed
        .sendcoin(transferTokenContractAddress, 1_000_000L, fromAddress, testKey002,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Long contractAssetCountAfter = PublicMethed
        .getAssetIssueValue(transferTokenContractAddress, assetAccountId, blockingStubFull);
    long contractAfetr = PublicMethed.queryAccount(transferTokenContractAddress, blockingStubFull)
        .getBalance();

    Assert.assertTrue(contractAssetCountBefore + 100L == contractAssetCountAfter);
    Assert.assertTrue(contractBefore + 1_000_000L == contractAfetr);

  }


  @Test(enabled = true, description = "Use create to generate a contract address "
      + "Sendcoin and transferAsset to contractAddresss ,then selfdestruct,")
  public void testSendCoinAndTransferAssetContract002() {

    assetAccountId = PublicMethed.queryAccount(dev001Address, blockingStubFull).getAssetIssuedID();

    logger.info("The token name: " + tokenName);
    logger.info("The token ID: " + assetAccountId.toStringUtf8());

    //before deploy, check account resource
    AccountResourceMessage accountResource = PublicMethed
        .getAccountResource(dev001Address, blockingStubFull);
    long entropyLimit = accountResource.getEntropyLimit();
    long entropyUsage = accountResource.getEntropyUsed();
    long balanceBefore = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();
    Long devAssetCountBefore = PublicMethed
        .getAssetIssueValue(dev001Address, assetAccountId, blockingStubFull);

    logger.info("before entropyLimit is " + Long.toString(entropyLimit));
    logger.info("before entropyUsage is " + Long.toString(entropyUsage));
    logger.info("before balanceBefore is " + Long.toString(balanceBefore));
    logger.info("before AssetId: " + assetAccountId.toStringUtf8() + ", devAssetCountBefore: "
        + devAssetCountBefore);

    String filePath = "src/test/resources/soliditycode/contractTransferToken001.sol";

    String contractName = "A";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    String tokenId = assetAccountId.toStringUtf8();
    long tokenValue = 100;
    long callValue = 5;

    final String deployContractTxid = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "", maxFeeLimit,
            callValue, 0, 10000, tokenId, tokenValue, null, dev001Key, dev001Address,
            blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(deployContractTxid, blockingStubFull);

    transferTokenContractAddress = infoById.get().getContractAddress().toByteArray();

    accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
    entropyLimit = accountResource.getEntropyLimit();
    entropyUsage = accountResource.getEntropyUsed();
    long balanceAfter = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();
    Long devAssetCountAfter = PublicMethed
        .getAssetIssueValue(dev001Address, assetAccountId, blockingStubFull);

    logger.info("after entropyLimit is " + Long.toString(entropyLimit));
    logger.info("after entropyUsage is " + Long.toString(entropyUsage));
    logger.info("after balanceAfter is " + Long.toString(balanceAfter));
    logger.info("after AssetId: " + assetAccountId.toStringUtf8() + ", devAssetCountAfter: "
        + devAssetCountAfter);

    String txid = PublicMethed
        .triggerContract(transferTokenContractAddress, "newB()", "#", false, 0, maxFeeLimit,
            dev001Address, dev001Key, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);

    byte[] a = infoById.get().getContractResult(0).toByteArray();
    byte[] b = subByte(a, 11, 1);
    byte[] c = subByte(a, 0, 11);
    byte[] e = "41".getBytes();
    byte[] d = subByte(a, 12, 20);

    logger.info("a:" + ByteArray.toHexString(a));

    logger.info("b:" + ByteArray.toHexString(b));
    logger.info("c:" + ByteArray.toHexString(c));

    logger.info("d:" + ByteArray.toHexString(d));

    logger.info("41" + ByteArray.toHexString(d));
    String exceptedResult = "41" + ByteArray.toHexString(d);
    String realResult = ByteArray.toHexString(b);
    Assert.assertEquals(realResult, "00");
    Assert.assertNotEquals(realResult, "41");

    String addressFinal = Base58.encode58Check(ByteArray.fromHexString(exceptedResult));
    logger.info("create Address : " + addressFinal);
    byte[] testContractAddress = WalletClient.decodeFromBase58Check(addressFinal);

    Return ret = PublicMethed
        .transferAssetForReturn(testContractAddress, assetAccountId.toByteArray(), 100L,
            dev001Address, dev001Key, blockingStubFull);

    Assert.assertEquals(CONTRACT_VALIDATE_ERROR, ret.getCode());
    Assert.assertEquals("contract validate error : Cannot transfer asset to smartContract.",
        ret.getMessage().toStringUtf8());
    Long contractAssetCount = PublicMethed
        .getAssetIssueValue(testContractAddress, assetAccountId, blockingStubFull);
    logger.info("Contract has AssetId: " + assetAccountId.toStringUtf8() + ", Count: "
        + contractAssetCount);

    Assert.assertEquals(Long.valueOf(0L), contractAssetCount);

    Return ret1 = PublicMethed
        .sendcoinForReturn(testContractAddress, 1_000_000L, fromAddress, testKey002,
            blockingStubFull);
    Assert.assertEquals(CONTRACT_VALIDATE_ERROR, ret1.getCode());
    Assert.assertEquals("contract validate error : Cannot transfer VS to a smartContract.",
        ret1.getMessage().toStringUtf8());

    String num = "\"" + Base58.encode58Check(dev001Address) + "\"";

    txid = PublicMethed
        .triggerContract(testContractAddress, "kill(address)", num, false, 0, maxFeeLimit,
            dev001Address, dev001Key, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Long contractAssetCountBefore = PublicMethed
        .getAssetIssueValue(testContractAddress, assetAccountId, blockingStubFull);
    long contractBefore = PublicMethed.queryAccount(testContractAddress, blockingStubFull)
        .getBalance();

    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);

    Assert.assertTrue(PublicMethed
        .transferAsset(testContractAddress, assetAccountId.toByteArray(), 100L, dev001Address,
            dev001Key, blockingStubFull));

    Assert.assertTrue(PublicMethed
        .sendcoin(testContractAddress, 1_000_000L, fromAddress, testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Long contractAssetCountAfter = PublicMethed
        .getAssetIssueValue(testContractAddress, assetAccountId, blockingStubFull);
    long contractAfetr = PublicMethed.queryAccount(testContractAddress, blockingStubFull)
        .getBalance();

    Assert.assertTrue(contractAssetCountBefore + 100L == contractAssetCountAfter);
    Assert.assertTrue(contractBefore + 1_000_000L == contractAfetr);
  }


  @Test(enabled = true, description = "Use create2 to generate a contract address \"\n"
      + "      + \"Sendcoin and transferAsset to contractAddresss ,then selfdestruct")
  public void testSendCoinAndTransferAssetContract003() {

    ECKey ecKey1 = new ECKey(Utils.getRandom());
    byte[] contractExcAddress = ecKey1.getAddress();
    String contractExcKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
    String sendcoin = PublicMethed
        .sendcoinGetTransactionId(contractExcAddress, 1000000000L, fromAddress, testKey002,
            blockingStubFull);

    Assert.assertTrue(PublicMethed
        .sendcoin(contractExcAddress, 1000000000L, fromAddress, testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById0 = null;
    infoById0 = PublicMethed.getTransactionInfoById(sendcoin, blockingStubFull);
    logger.info("infoById0   " + infoById0.get());
    Assert.assertEquals(ByteArray.toHexString(infoById0.get().getContractResult(0).toByteArray()),
        "");
    Assert.assertEquals(infoById0.get().getResult().getNumber(), 0);
    Optional<Transaction> ById = PublicMethed.getTransactionById(sendcoin, blockingStubFull);
    Assert.assertEquals(ById.get().getRet(0).getContractRet().getNumber(), SUCCESS_VALUE);
    Assert.assertEquals(ById.get().getRet(0).getContractRetValue(), SUCCESS_VALUE);
    Assert.assertEquals(ById.get().getRet(0).getContractRet(), contractResult.SUCCESS);
    String filePath = "src/test/resources/soliditycode/create2contractn2.sol";
    String contractName = "Factory";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    byte[] contractAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit, 0L, 100, null, contractExcKey,
            contractExcAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account info;

    AccountResourceMessage resourceInfo = PublicMethed
        .getAccountResource(contractExcAddress, blockingStubFull);
    info = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    Long beforePhotonUsed = resourceInfo.getPhotonUsed();
    Long beforeFreePhotonUsed = resourceInfo.getFreePhotonUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforePhotonUsed:" + beforePhotonUsed);
    logger.info("beforeFreePhotonUsed:" + beforeFreePhotonUsed);

    String contractName1 = "TestConstract";
    HashMap retMap1 = PublicMethed.getBycodeAbi(filePath, contractName1);
    String code1 = retMap1.get("byteCode").toString();
    String abi1 = retMap1.get("abI").toString();
    String txid = "";
    String num = "\"" + code1 + "\"" + "," + 1;
    txid = PublicMethed
        .triggerContract(contractAddress, "deploy(bytes,uint256)", num, false, 0, maxFeeLimit, "0",
            0, contractExcAddress, contractExcKey, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Long fee = infoById.get().getFee();
    Long photonUsed = infoById.get().getReceipt().getPhotonUsage();
    Long entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    Long photonFee = infoById.get().getReceipt().getPhotonFee();
    long entropyUsageTotal = infoById.get().getReceipt().getEntropyUsageTotal();

    logger.info("fee:" + fee);
    logger.info("photonUsed:" + photonUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);
    logger.info("entropyUsageTotal:" + entropyUsageTotal);

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    AccountResourceMessage resourceInfoafter = PublicMethed
        .getAccountResource(contractExcAddress, blockingStubFull);
    Long afterBalance = infoafter.getBalance();
    Long afterEntropyUsed = resourceInfoafter.getEntropyUsed();
    Long afterPhotonUsed = resourceInfoafter.getPhotonUsed();
    Long afterFreePhotonUsed = resourceInfoafter.getFreePhotonUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEntropyUsed:" + afterEntropyUsed);
    logger.info("afterPhotonUsed:" + afterPhotonUsed);
    logger.info("afterFreePhotonUsed:" + afterFreePhotonUsed);

    Assert.assertTrue(infoById.get().getResultValue() == 0);
    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(beforeEntropyUsed + entropyUsed >= afterEntropyUsed);
    Assert.assertTrue(beforeFreePhotonUsed + photonUsed >= afterFreePhotonUsed);
    Assert.assertTrue(beforePhotonUsed + photonUsed >= afterPhotonUsed);
    byte[] returnAddressBytes = infoById.get().getInternalTransactions(0).getTransferToAddress()
        .toByteArray();
    String returnAddress = Base58.encode58Check(returnAddressBytes);
    logger.info("returnAddress:" + returnAddress);

    Return ret = PublicMethed
        .transferAssetForReturn(returnAddressBytes, assetAccountId.toByteArray(), 100L,
            dev001Address, dev001Key, blockingStubFull);

    Assert.assertEquals(CONTRACT_VALIDATE_ERROR, ret.getCode());
    Assert.assertEquals("contract validate error : Cannot transfer asset to smartContract.",
        ret.getMessage().toStringUtf8());

    Return ret1 = PublicMethed
        .transferAssetForReturn(returnAddressBytes, assetAccountId.toByteArray(), 100L,
            dev001Address, dev001Key, blockingStubFull);

    Assert.assertEquals(CONTRACT_VALIDATE_ERROR, ret1.getCode());
    Assert.assertEquals("contract validate error : Cannot transfer asset to smartContract.",
        ret1.getMessage().toStringUtf8());

    txid = PublicMethed
        .triggerContract(returnAddressBytes, "i()", "#", false, 0, maxFeeLimit, "0", 0,
            contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById1 = null;
    infoById1 = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Long fee1 = infoById1.get().getFee();
    Long photonUsed1 = infoById1.get().getReceipt().getPhotonUsage();
    Long entropyUsed1 = infoById1.get().getReceipt().getEntropyUsage();
    Long photonFee1 = infoById1.get().getReceipt().getPhotonFee();
    long entropyUsageTotal1 = infoById1.get().getReceipt().getEntropyUsageTotal();

    logger.info("fee1:" + fee1);
    logger.info("photonUsed1:" + photonUsed1);
    logger.info("entropyUsed1:" + entropyUsed1);
    logger.info("photonFee1:" + photonFee1);
    logger.info("entropyUsageTotal1:" + entropyUsageTotal1);

    Account infoafter1 = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    AccountResourceMessage resourceInfoafter1 = PublicMethed
        .getAccountResource(contractExcAddress, blockingStubFull);
    Long afterBalance1 = infoafter1.getBalance();
    Long afterEntropyUsed1 = resourceInfoafter1.getEntropyUsed();
    Long afterPhotonUsed1 = resourceInfoafter1.getPhotonUsed();
    Long afterFreePhotonUsed1 = resourceInfoafter1.getFreePhotonUsed();
    logger.info("afterBalance:" + afterBalance1);
    logger.info("afterEntropyUsed:" + afterEntropyUsed1);
    logger.info("afterPhotonUsed:" + afterPhotonUsed1);
    logger.info("afterFreePhotonUsed:" + afterFreePhotonUsed1);

    Assert.assertTrue(infoById1.get().getResultValue() == 0);
    Assert.assertTrue(afterBalance1 + fee1 == afterBalance);
    Assert.assertTrue(afterEntropyUsed + entropyUsed1 >= afterEntropyUsed1);
    Long returnnumber = ByteArray.toLong(ByteArray
        .fromHexString(ByteArray.toHexString(infoById1.get().getContractResult(0).toByteArray())));
    Assert.assertTrue(1 == returnnumber);
    txid = PublicMethed
        .triggerContract(returnAddressBytes, "set()", "#", false, 0, maxFeeLimit, "0", 0,
            contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    txid = PublicMethed
        .triggerContract(returnAddressBytes, "i()", "#", false, 0, maxFeeLimit, "0", 0,
            contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById1 = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    returnnumber = ByteArray.toLong(ByteArray
        .fromHexString(ByteArray.toHexString(infoById1.get().getContractResult(0).toByteArray())));
    Assert.assertTrue(5 == returnnumber);

    String param1 = "\"" + Base58.encode58Check(returnAddressBytes) + "\"";

    txid = PublicMethed
        .triggerContract(returnAddressBytes, "testSuicideNonexistentTarget(address)", param1, false,
            0, maxFeeLimit, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById2 = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);

    Assert.assertEquals("suicide",
        ByteArray.toStr(infoById2.get().getInternalTransactions(0).getNote().toByteArray()));
    TransactionExtention transactionExtention = PublicMethed
        .triggerContractForExtention(returnAddressBytes, "i()", "#", false, 0, maxFeeLimit, "0", 0,
            contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert.assertThat(transactionExtention.getResult().getCode().toString(),
        containsString("CONTRACT_VALIDATE_ERROR"));
    Assert.assertThat(transactionExtention.getResult().getMessage().toStringUtf8(),
        containsString("contract validate error : No contract or not a valid smart contract"));

    Assert.assertTrue(PublicMethed
        .transferAsset(returnAddressBytes, assetAccountId.toByteArray(), 100L, dev001Address,
            dev001Key, blockingStubFull));

    Assert.assertTrue(PublicMethed
        .sendcoin(returnAddressBytes, 1_000_000L, fromAddress, testKey002, blockingStubFull));

    txid = PublicMethed
        .triggerContract(contractAddress, "deploy(bytes,uint256)", num, false, 0, maxFeeLimit, "0",
            0, contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Optional<TransactionInfo> infoById3 = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    byte[] returnAddressBytes1 = infoById3.get().getInternalTransactions(0).getTransferToAddress()
        .toByteArray();
    String returnAddress1 = Base58.encode58Check(returnAddressBytes1);
    Assert.assertEquals(returnAddress1, returnAddress);
    txid = PublicMethed
        .triggerContract(returnAddressBytes1, "i()", "#", false, 0, maxFeeLimit, "0", 0,
            contractExcAddress, contractExcKey, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById1 = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    returnnumber = ByteArray.toLong(ByteArray
        .fromHexString(ByteArray.toHexString(infoById1.get().getContractResult(0).toByteArray())));
    Assert.assertTrue(1 == returnnumber);

    ret = PublicMethed
        .transferAssetForReturn(returnAddressBytes1, assetAccountId.toByteArray(), 100L,
            dev001Address, dev001Key, blockingStubFull);

    Assert.assertEquals(CONTRACT_VALIDATE_ERROR, ret.getCode());
    Assert.assertEquals("contract validate error : Cannot transfer asset to smartContract.",
        ret.getMessage().toStringUtf8());

    ret1 = PublicMethed
        .transferAssetForReturn(returnAddressBytes1, assetAccountId.toByteArray(), 100L,
            dev001Address, dev001Key, blockingStubFull);

    Assert.assertEquals(CONTRACT_VALIDATE_ERROR, ret1.getCode());
    Assert.assertEquals("contract validate error : Cannot transfer asset to smartContract.",
        ret1.getMessage().toStringUtf8());

  }


  public byte[] subByte(byte[] b, int off, int length) {
    byte[] b1 = new byte[length];
    System.arraycopy(b, off, b1, 0, length);
    return b1;

  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed.freedResource(dev001Address, dev001Key, fromAddress, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


