package stest.vision.wallet.dailybuild.vvmnewcommand.batchValidateSignContract;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.spongycastle.util.encoders.Hex;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.vision.api.GrpcAPI;
import org.vision.api.GrpcAPI.TransactionExtention;
import org.vision.api.WalletGrpc;
import org.vision.common.crypto.ECKey;
import org.vision.common.crypto.Hash;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.StringUtil;
import org.vision.common.utils.Utils;
import org.vision.core.Wallet;
import org.vision.protos.Protocol;
import org.vision.protos.Protocol.TransactionInfo;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class batchValidateSignContract007 {

  private final String testNetAccountKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] testNetAccountAddress = PublicMethed.getFinalAddress(testNetAccountKey);
  byte[] contractAddress = null;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contractExcAddress = ecKey1.getAddress();
  String contractExcKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private ManagedChannel channelFull1 = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);

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
    PublicMethed.printAddress(contractExcKey);
    channelFull = ManagedChannelBuilder.forTarget(fullnode).usePlaintext(true).build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1).usePlaintext(true).build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);
  }

  @Test(enabled = true, description = "Constructor test multivalidatesign")
  public void test01Constructor() {
    String txid = PublicMethed
        .sendcoinGetTransactionId(contractExcAddress, 2000000000L, testNetAccountAddress,
            testNetAccountKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    GrpcAPI.AccountResourceMessage resourceInfo = PublicMethed
        .getAccountResource(contractExcAddress, blockingStubFull);
    Protocol.Account info = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    Long beforePhotonUsed = resourceInfo.getPhotonUsed();
    Long beforeFreePhotonUsed = resourceInfo.getFreePhotonUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforePhotonUsed:" + beforePhotonUsed);
    logger.info("beforeFreePhotonUsed:" + beforeFreePhotonUsed);

    String filePath = "src/test/resources/soliditycode/batchvalidatesign007.sol";
    String contractName = "Demo";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    List<Object> signatures = new ArrayList<>();
    List<Object> addresses = new ArrayList<>();
    byte[] hash = Hash.sha3(txid.getBytes());
    for (int i = 0; i < 16; i++) {
      ECKey key = new ECKey();
      byte[] sign = key.sign(hash).toByteArray();
      signatures.add(Hex.toHexString(sign));
      addresses.add(StringUtil.encode58Check(key.getAddress()));
    }
    List<Object> parameters = Arrays.asList("0x" + Hex.toHexString(hash), signatures, addresses);
    String data = PublicMethed.parametersString(parameters);
    String constructorStr = "constructor(bytes32,bytes[],address[])";
    txid = PublicMethed
        .deployContractWithConstantParame(contractName, abi, code, constructorStr, data, "",
            maxFeeLimit, 0L, 100, null, contractExcKey, contractExcAddress, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertEquals(0, infoById.get().getResultValue());
    Long fee1 = infoById.get().getFee();
    Long photonUsed1 = infoById.get().getReceipt().getPhotonUsage();
    Long entropyUsed1 = infoById.get().getReceipt().getEntropyUsage();
    Long photonFee1 = infoById.get().getReceipt().getPhotonFee();
    long entropyUsageTotal1 = infoById.get().getReceipt().getEntropyUsageTotal();
    logger.info("fee1:" + fee1);
    logger.info("photonUsed1:" + photonUsed1);
    logger.info("entropyUsed1:" + entropyUsed1);
    logger.info("photonFee1:" + photonFee1);
    logger.info("entropyUsageTotal1:" + entropyUsageTotal1);
    contractAddress = infoById.get().getContractAddress().toByteArray();

    TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtention(contractAddress, "testConstructorPure()", "", false, 0,
            0, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);

    Assert.assertEquals("11111111111111110000000000000000",
        PublicMethed.bytes32ToString(transactionExtention.getConstantResult(0).toByteArray()));
    Assert.assertEquals("SUCCESS", transactionExtention.getResult().getCode().toString());

    txid = PublicMethed
        .triggerContract(contractAddress, "testConstructor()", "", false, 0, maxFeeLimit,
            contractExcAddress, contractExcKey, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById2 = null;
    infoById2 = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    if (infoById2.get().getResultValue() == 0) {
      Assert.assertEquals("11111111111111110000000000000000",
          PublicMethed.bytes32ToString(infoById2.get().getContractResult(0).toByteArray()));
    } else {
      Assert.assertTrue("CPU timeout for 'PUSH1' operation executing"
          .equals(infoById2.get().getResMessage().toStringUtf8()) || "Already Time Out"
          .equals(infoById2.get().getResMessage().toStringUtf8()));
      PublicMethed.waitProduceNextBlock(blockingStubFull);
    }
    Long fee2 = infoById2.get().getFee();
    Long photonUsed2 = infoById2.get().getReceipt().getPhotonUsage();
    Long entropyUsed2 = infoById2.get().getReceipt().getEntropyUsage();
    Long photonFee2 = infoById2.get().getReceipt().getPhotonFee();
    long entropyUsageTotal2 = infoById2.get().getReceipt().getEntropyUsageTotal();
    logger.info("fee2:" + fee2);
    logger.info("photonUsed2:" + photonUsed2);
    logger.info("entropyUsed2:" + entropyUsed2);
    logger.info("photonFee2:" + photonFee2);
    logger.info("entropyUsageTotal2:" + entropyUsageTotal2);

    Protocol.Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull1);
    GrpcAPI.AccountResourceMessage resourceInfoafter = PublicMethed
        .getAccountResource(contractExcAddress, blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterEntropyUsed = resourceInfoafter.getEntropyUsed();
    Long afterPhotonUsed = resourceInfoafter.getPhotonUsed();
    Long afterFreePhotonUsed = resourceInfoafter.getFreePhotonUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEntropyUsed:" + afterEntropyUsed);
    logger.info("afterPhotonUsed:" + afterPhotonUsed);
    logger.info("afterFreePhotonUsed:" + afterFreePhotonUsed);
    Assert.assertTrue(afterBalance + fee1 + fee2 == beforeBalance);
    Assert.assertTrue(beforeEntropyUsed + entropyUsed1 + entropyUsed2 >= afterEntropyUsed);
    Assert.assertTrue(beforeFreePhotonUsed + photonUsed1 + photonUsed2 >= afterFreePhotonUsed);
    Assert.assertTrue(beforePhotonUsed + photonUsed1 + photonUsed2 >= afterPhotonUsed);
  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    long balance = PublicMethed.queryAccount(contractExcKey, blockingStubFull).getBalance();
    PublicMethed.sendcoin(testNetAccountAddress, balance, contractExcAddress, contractExcKey,
        blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelFull1 != null) {
      channelFull1.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}
