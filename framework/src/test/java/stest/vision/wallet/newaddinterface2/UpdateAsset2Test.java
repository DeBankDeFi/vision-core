package stest.vision.wallet.newaddinterface2;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Hex;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.vision.api.GrpcAPI;
import org.vision.api.GrpcAPI.NumberMessage;
import org.vision.api.GrpcAPI.Return;
import org.vision.api.WalletGrpc;
import org.vision.common.crypto.ECKey;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Utils;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.Account;
import org.vision.protos.Protocol.Block;
import org.vision.protos.Protocol.Transaction;
import org.vision.protos.contract.AssetIssueContractOuterClass;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter;
import stest.vision.wallet.common.client.utils.PublicMethed;
import stest.vision.wallet.common.client.utils.TransactionUtils;

@Slf4j
public class UpdateAsset2Test {

  private static final long now = System.currentTimeMillis();
  private static final long totalSupply = now;
  private static final long sendAmount = 10000000000L;
  private static final String tooLongDescription =
      "1qazxswedcvqazxswedcvqazxswedcvqazxswedcvqazxswedcvqazxswedcvqazxswedcvqazxswedcv"
          + "qazxswedcvqazxswedcvqazxswedcvqazxswedcvqazxswedcvqazxswedcvqazxswedcvqazxswe"
          + "dcvqazxswedcvqazxswedcvqazxswedcvqazxswedcv";
  private static final String tooLongUrl = "qaswqaswqaswqaswqaswqaswqaswqaswqaswqaswqaswqas"
      + "wqaswqasw1qazxswedcvqazxswedcvqazxswedcvqazxswedcvqazxswedcvqazxswedcvqazxswedcvqazx"
      + "swedcvqazxswedcvqazxswedcvqazxswedcvqazxswedcvqazxswedcvqazxswedcvqazxswedcvqazxswedc"
      + "vqazxswedcvqazxswedcvqazxswedcvqazxswedcv";
  private static String name = "testAssetIssue010_" + Long.toString(now);
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  String description = "just-test";
  String url = "https://github.com/vworldgenesis/wallet-cli/";
  String updateDescription = "This is test for update asset issue, case AssetIssue_010";
  String updateUrl = "www.updateassetissue.010.cn";
  Long freeAssetPhotonLimit = 1000L;
  Long publicFreeAssetPhotonLimit = 1000L;
  Long updateFreeAssetPhotonLimit = 10001L;
  Long updatePublicFreeAssetPhotonLimit = 10001L;
  //get account
  ECKey ecKey = new ECKey(Utils.getRandom());
  byte[] asset010Address = ecKey.getAddress();
  String testKeyForAssetIssue010 = ByteArray.toHexString(ecKey.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);

  public static String loadPubKey() {
    char[] buf = new char[0x100];
    return String.valueOf(buf, 32, 130);
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

  @Test(enabled = true)
  public void testUpdateAssetIssue2() {
    //Sendcoin to this account
    ByteString addressBS1 = ByteString.copyFrom(asset010Address);
    Account request1 = Account.newBuilder().setAddress(addressBS1).build();
    GrpcAPI.AssetIssueList assetIssueList1 = blockingStubFull
        .getAssetIssueByAccount(request1);
    Optional<GrpcAPI.AssetIssueList> queryAssetByAccount = Optional.ofNullable(assetIssueList1);
    if (queryAssetByAccount.get().getAssetIssueCount() == 0) {
      //Assert.assertTrue(PublicMethed.freezeBalance(fromAddress,10000000L,3,
      //    testKey002,blockingStubFull));
      Assert.assertTrue(PublicMethed
          .sendcoin(asset010Address, sendAmount, fromAddress, testKey002, blockingStubFull));
      Assert.assertTrue(PublicMethed
          .freezeBalance(asset010Address, 200000000L, 3, testKeyForAssetIssue010,
              blockingStubFull));
      Long start = System.currentTimeMillis() + 2000;
      Long end = System.currentTimeMillis() + 1000000000;
      Assert.assertTrue(PublicMethed.createAssetIssue(asset010Address, name, totalSupply, 1, 1,
          start, end, 1, description, url, freeAssetPhotonLimit, publicFreeAssetPhotonLimit,
          1L, 1L, testKeyForAssetIssue010, blockingStubFull));
    } else {
      logger.info("This account already create an assetisue");
      Optional<GrpcAPI.AssetIssueList> queryAssetByAccount1 = Optional.ofNullable(assetIssueList1);
      name = ByteArray.toStr(queryAssetByAccount1.get().getAssetIssue(0).getName().toByteArray());
      Assert.assertTrue(PublicMethed
          .updateAsset(asset010Address, description.getBytes(), url.getBytes(), freeAssetPhotonLimit,
                  publicFreeAssetPhotonLimit, testKeyForAssetIssue010, blockingStubFull));
    }

    //Query the description and url,freeAssetPhotonLimit and publicFreeAssetPhotonLimit
    ByteString assetNameBs = ByteString.copyFrom(name.getBytes());
    GrpcAPI.BytesMessage request = GrpcAPI.BytesMessage.newBuilder().setValue(assetNameBs).build();
    AssetIssueContractOuterClass.AssetIssueContract assetIssueByName = blockingStubFull
        .getAssetIssueByName(request);

    Assert.assertTrue(
        ByteArray.toStr(assetIssueByName.getDescription().toByteArray()).equals(description));
    Assert.assertTrue(ByteArray.toStr(assetIssueByName.getUrl().toByteArray()).equals(url));
    Assert.assertTrue(assetIssueByName.getFreeAssetPhotonLimit() == freeAssetPhotonLimit);
    Assert.assertTrue(assetIssueByName.getPublicFreeAssetPhotonLimit() == publicFreeAssetPhotonLimit);

    //Test update asset issue
    Return ret1 = PublicMethed
        .updateAsset2(asset010Address, updateDescription.getBytes(), updateUrl.getBytes(),
                updateFreeAssetPhotonLimit,
                updatePublicFreeAssetPhotonLimit, testKeyForAssetIssue010, blockingStubFull);
    Assert.assertEquals(ret1.getCode(), Return.response_code.SUCCESS);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(), "");

    //After update asset issue ,query the description and url,
    // freeAssetPhotonLimit and publicFreeAssetPhotonLimit
    assetNameBs = ByteString.copyFrom(name.getBytes());
    request = GrpcAPI.BytesMessage.newBuilder().setValue(assetNameBs).build();
    assetIssueByName = blockingStubFull.getAssetIssueByName(request);

    Assert.assertTrue(
        ByteArray.toStr(assetIssueByName.getDescription().toByteArray()).equals(updateDescription));
    Assert.assertTrue(ByteArray.toStr(assetIssueByName.getUrl().toByteArray()).equals(updateUrl));
    Assert.assertTrue(assetIssueByName.getFreeAssetPhotonLimit() == updateFreeAssetPhotonLimit);
    Assert
        .assertTrue(assetIssueByName.getPublicFreeAssetPhotonLimit() == updatePublicFreeAssetPhotonLimit);
  }

  @Test(enabled = true)
  public void testUpdateAssetIssueExcption2() {
    //Test update asset issue for wrong parameter
    //publicFreeAssetPhotonLimit is -1
    Return ret1 = PublicMethed
        .updateAsset2(asset010Address, updateDescription.getBytes(), updateUrl.getBytes(),
                updateFreeAssetPhotonLimit,
            -1L, testKeyForAssetIssue010, blockingStubFull);
    Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(),
        "contract validate error : Invalid PublicFreeAssetPhotonLimit");
    //publicFreeAssetPhotonLimit is 0
    ret1 = PublicMethed
        .updateAsset2(asset010Address, updateDescription.getBytes(), updateUrl.getBytes(),
                updateFreeAssetPhotonLimit,
            0, testKeyForAssetIssue010, blockingStubFull);
    Assert.assertEquals(ret1.getCode(), Return.response_code.SUCCESS);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(), "");
    //FreeAssetPhotonLimit is -1
    ret1 = PublicMethed
        .updateAsset2(asset010Address, updateDescription.getBytes(), updateUrl.getBytes(), -1,
                publicFreeAssetPhotonLimit, testKeyForAssetIssue010, blockingStubFull);
    Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(),
        "contract validate error : Invalid FreeAssetPhotonLimit");
    //FreeAssetPhotonLimit is 0
    ret1 = PublicMethed
        .updateAsset2(asset010Address, updateDescription.getBytes(), updateUrl.getBytes(), 0,
                publicFreeAssetPhotonLimit, testKeyForAssetIssue010, blockingStubFull);
    Assert.assertEquals(ret1.getCode(), Return.response_code.SUCCESS);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(), "");
    //Description is null
    ret1 = PublicMethed
        .updateAsset2(asset010Address, "".getBytes(), updateUrl.getBytes(), freeAssetPhotonLimit,
                publicFreeAssetPhotonLimit, testKeyForAssetIssue010, blockingStubFull);
    Assert.assertEquals(ret1.getCode(), Return.response_code.SUCCESS);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(), "");
    //Url is null
    ret1 = PublicMethed
        .updateAsset2(asset010Address, description.getBytes(), "".getBytes(), freeAssetPhotonLimit,
                publicFreeAssetPhotonLimit, testKeyForAssetIssue010, blockingStubFull);
    Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(), "contract validate error : Invalid url");
    //Too long discription
    ret1 = PublicMethed
        .updateAsset2(asset010Address, tooLongDescription.getBytes(), url.getBytes(),
                freeAssetPhotonLimit,
                publicFreeAssetPhotonLimit, testKeyForAssetIssue010, blockingStubFull);
    Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(),
        "contract validate error : Invalid description");
    //Too long URL
    ret1 = PublicMethed
        .updateAsset2(asset010Address, description.getBytes(), tooLongUrl.getBytes(),
                freeAssetPhotonLimit,
                publicFreeAssetPhotonLimit, testKeyForAssetIssue010, blockingStubFull);
    Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(), "contract validate error : Invalid url");
  }

  /**
   * constructor.
   */

  @AfterClass(enabled = true)
  public void shutdown() throws InterruptedException {
    Return ret1 = PublicMethed
        .updateAsset2(asset010Address, description.getBytes(), url.getBytes(), 1999999999,
            199, testKeyForAssetIssue010, blockingStubFull);
    Assert.assertEquals(ret1.getCode(), Return.response_code.SUCCESS);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(), "");
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  /**
   * constructor.
   */

  public Boolean createAssetIssue(byte[] address, String name, Long totalSupply, Integer vsNum,
      Integer icoNum, Long startTime, Long endTime,
      Integer voteScore, String description, String url, Long fronzenAmount, Long frozenDay,
      String priKey) {
    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    ECKey ecKey = temKey;
    Account search = PublicMethed.queryAccount(priKey, blockingStubFull);

    try {
      AssetIssueContractOuterClass.AssetIssueContract.Builder builder =
          AssetIssueContractOuterClass.AssetIssueContract
              .newBuilder();
      builder.setOwnerAddress(ByteString.copyFrom(address));
      builder.setName(ByteString.copyFrom(name.getBytes()));
      builder.setTotalSupply(totalSupply);
      builder.setVsNum(vsNum);
      builder.setNum(icoNum);
      builder.setStartTime(startTime);
      builder.setEndTime(endTime);
      builder.setVoteScore(voteScore);
      builder.setDescription(ByteString.copyFrom(description.getBytes()));
      builder.setUrl(ByteString.copyFrom(url.getBytes()));
      AssetIssueContractOuterClass.AssetIssueContract.FrozenSupply.Builder frozenBuilder =
          AssetIssueContractOuterClass.AssetIssueContract.FrozenSupply
              .newBuilder();
      frozenBuilder.setFrozenAmount(fronzenAmount);
      frozenBuilder.setFrozenDays(frozenDay);
      builder.addFrozenSupply(0, frozenBuilder);

      Transaction transaction = blockingStubFull.createAssetIssue(builder.build());
      if (transaction == null || transaction.getRawData().getContractCount() == 0) {
        logger.info("transaction == null");
        return false;
      }
      transaction = signTransaction(ecKey, transaction);
      Return response = blockingStubFull.broadcastTransaction(transaction);
      if (!response.getResult()) {
        logger.info(ByteArray.toStr(response.getMessage().toByteArray()));
        return false;
      } else {
        logger.info(name);
        return true;
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      return false;
    }
  }

  /**
   * constructor.
   */

  public Account queryAccount(ECKey ecKey, WalletGrpc.WalletBlockingStub blockingStubFull) {
    byte[] address;
    if (ecKey == null) {
      String pubKey = loadPubKey(); //04 PubKey[128]
      if (StringUtils.isEmpty(pubKey)) {
        logger.warn("Warning: QueryAccount failed, no wallet address !!");
        return null;
      }
      byte[] pubKeyAsc = pubKey.getBytes();
      byte[] pubKeyHex = Hex.decode(pubKeyAsc);
      ecKey = ECKey.fromPublicOnly(pubKeyHex);
    }
    return grpcQueryAccount(ecKey.getAddress(), blockingStubFull);
  }

  public byte[] getAddress(ECKey ecKey) {
    return ecKey.getAddress();
  }

  /**
   * constructor.
   */

  public Account grpcQueryAccount(byte[] address, WalletGrpc.WalletBlockingStub blockingStubFull) {
    ByteString addressBs = ByteString.copyFrom(address);
    Account request = Account.newBuilder().setAddress(addressBs).build();
    return blockingStubFull.getAccount(request);
  }

  /**
   * constructor.
   */

  public Block getBlock(long blockNum, WalletGrpc.WalletBlockingStub blockingStubFull) {
    NumberMessage.Builder builder = NumberMessage.newBuilder();
    builder.setNum(blockNum);
    return blockingStubFull.getBlockByNum(builder.build());

  }

  private Transaction signTransaction(ECKey ecKey, Transaction transaction) {
    if (ecKey == null || ecKey.getPrivKey() == null) {
      logger.warn("Warning: Can't sign,there is no private key !!");
      return null;
    }
    transaction = TransactionUtils.setTimestamp(transaction);
    return TransactionUtils.sign(transaction, ecKey);
  }

  /**
   * constructor.
   */

  public boolean transferAsset(byte[] to, byte[] assertName, long amount, byte[] address,
      String priKey) {
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
    Transaction transaction = blockingStubFull.transferAsset(contract);
    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      return false;
    }
    transaction = signTransaction(ecKey, transaction);
    Return response = blockingStubFull.broadcastTransaction(transaction);
    if (response.getResult() == false) {
      return false;
    } else {
      Account search = queryAccount(ecKey, blockingStubFull);
      return true;
    }

  }

  /**
   * constructor.
   */

  public boolean unFreezeAsset(byte[] addRess, String priKey) {
    byte[] address = addRess;

    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    final ECKey ecKey = temKey;

    AssetIssueContractOuterClass.UnfreezeAssetContract.Builder builder =
        AssetIssueContractOuterClass.UnfreezeAssetContract
            .newBuilder();
    ByteString byteAddreess = ByteString.copyFrom(address);

    builder.setOwnerAddress(byteAddreess);

    AssetIssueContractOuterClass.UnfreezeAssetContract contract = builder.build();

    Transaction transaction = blockingStubFull.unfreezeAsset(contract);

    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      return false;
    }

    transaction = TransactionUtils.setTimestamp(transaction);
    transaction = TransactionUtils.sign(transaction, ecKey);
    Return response = blockingStubFull.broadcastTransaction(transaction);
    if (response.getResult() == false) {
      logger.info(ByteArray.toStr(response.getMessage().toByteArray()));
      return false;
    } else {
      return true;
    }
  }

  /**
   * constructor.
   */

  public boolean participateAssetIssue(byte[] to, byte[] assertName, long amount, byte[] from,
      String priKey) {
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

    Transaction transaction = blockingStubFull.participateAssetIssue(contract);
    transaction = signTransaction(ecKey, transaction);
    Return response = blockingStubFull.broadcastTransaction(transaction);
    if (response.getResult() == false) {
      logger.info(ByteArray.toStr(response.getMessage().toByteArray()));
      return false;
    } else {
      logger.info(name);
      return true;
    }
  }

}


