package stest.vision.wallet.dailybuild.assetissue;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.vision.api.GrpcAPI.AccountPhotonMessage;
import org.vision.api.WalletGrpc;
import org.vision.common.crypto.ECKey;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Utils;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.Account;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class WalletTestAssetIssue012 {

  private static final long now = System.currentTimeMillis();
  private static final long totalSupply = now;
  private static final long sendAmount = 10000000000L;
  private static final long netCostMeasure = 200L;
  private static String name = "AssetIssue012_" + Long.toString(now);
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
  Long freeAssetPhotonLimit = 10000L;
  Long publicFreeAssetPhotonLimit = 10000L;
  String description = "for case assetissue012";
  String url = "https://stest.assetissue012.url";
  //get account
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] asset012Address = ecKey1.getAddress();
  String testKeyForAssetIssue012 = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] transferAssetAddress = ecKey2.getAddress();
  String transferAssetCreateKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);

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
    logger.info(testKeyForAssetIssue012);
    logger.info(transferAssetCreateKey);

    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
  }

  @Test(enabled = true, description = "Transfer asset use token owner net")
  public void testTransferAssetUseCreatorNet() {
    //get account
    ecKey1 = new ECKey(Utils.getRandom());
    asset012Address = ecKey1.getAddress();
    testKeyForAssetIssue012 = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

    ecKey2 = new ECKey(Utils.getRandom());
    transferAssetAddress = ecKey2.getAddress();
    transferAssetCreateKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

    PublicMethed.printAddress(testKeyForAssetIssue012);
    PublicMethed.printAddress(transferAssetCreateKey);

    Assert.assertTrue(PublicMethed
        .sendcoin(asset012Address, sendAmount, fromAddress, testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethed
        .freezeBalance(asset012Address, 100000000L, 3, testKeyForAssetIssue012,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Long start = System.currentTimeMillis() + 2000;
    Long end = System.currentTimeMillis() + 1000000000;
    Assert.assertTrue(PublicMethed
        .createAssetIssue(asset012Address, name, totalSupply, 1, 1, start, end, 1, description,
            url, freeAssetPhotonLimit, publicFreeAssetPhotonLimit, 1L, 1L, testKeyForAssetIssue012,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account getAssetIdFromThisAccount;
    getAssetIdFromThisAccount = PublicMethed.queryAccount(asset012Address, blockingStubFull);
    ByteString assetAccountId = getAssetIdFromThisAccount.getAssetIssuedID();

    //Transfer asset to an account.
    Assert.assertTrue(PublicMethed.transferAsset(
        transferAssetAddress, assetAccountId.toByteArray(), 10000000L, asset012Address,
        testKeyForAssetIssue012, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    //Before transfer asset issue, query the net used from creator and transfer.
    AccountPhotonMessage assetCreatorNet = PublicMethed
        .getAccountPhoton(asset012Address, blockingStubFull);
    AccountPhotonMessage assetTransferNet = PublicMethed
        .getAccountPhoton(transferAssetAddress, blockingStubFull);
    Long creatorBeforePhotonUsed = assetCreatorNet.getPhotonUsed();
    Long transferBeforeFreePhotonUsed = assetTransferNet.getFreePhotonUsed();
    logger.info(Long.toString(creatorBeforePhotonUsed));
    logger.info(Long.toString(transferBeforeFreePhotonUsed));

    //Transfer send some asset issue to default account, to test if this
    // transaction use the creator Photon.
    Assert.assertTrue(PublicMethed.transferAsset(toAddress, assetAccountId.toByteArray(), 1L,
        transferAssetAddress, transferAssetCreateKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    assetCreatorNet = PublicMethed
        .getAccountPhoton(asset012Address, blockingStubFull);
    assetTransferNet = PublicMethed
        .getAccountPhoton(transferAssetAddress, blockingStubFull);
    Long creatorAfterPhotonUsed = assetCreatorNet.getPhotonUsed();
    Long transferAfterFreePhotonUsed = assetTransferNet.getFreePhotonUsed();
    logger.info(Long.toString(creatorAfterPhotonUsed));
    logger.info(Long.toString(transferAfterFreePhotonUsed));

    Assert.assertTrue(creatorAfterPhotonUsed - creatorBeforePhotonUsed > netCostMeasure);
    Assert.assertTrue(transferAfterFreePhotonUsed - transferBeforeFreePhotonUsed < netCostMeasure);

    PublicMethed
        .freedResource(asset012Address, testKeyForAssetIssue012, fromAddress, blockingStubFull);
    PublicMethed.unFreezeBalance(asset012Address, testKeyForAssetIssue012, 0, asset012Address,
        blockingStubFull);
  }

  /**
   * constructor.
   */

  @AfterClass(enabled = true)
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


