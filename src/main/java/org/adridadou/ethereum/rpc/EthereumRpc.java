package org.adridadou.ethereum.rpc;

import org.adridadou.ethereum.propeller.Crypto;
import org.adridadou.ethereum.propeller.EthereumBackend;
import org.adridadou.ethereum.propeller.event.*;
import org.adridadou.ethereum.propeller.values.*;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.core.methods.request.RawTransaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.utils.Numeric;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by davidroon on 20.01.17.
 * This code is released under Apache 2 license
 */
public class EthereumRpc implements EthereumBackend {
    private final Web3JFacade web3JFacade;
    private final EthereumRpcEventGenerator ethereumRpcEventGenerator;
    private final ChainId chainId;

    public EthereumRpc(Web3JFacade web3JFacade, ChainId chainId, EthereumRpcConfig config) {
        this.web3JFacade = web3JFacade;
        this.ethereumRpcEventGenerator = new EthereumRpcEventGenerator(web3JFacade, config, this);
        this.chainId = chainId;
    }

    @Override
    public GasPrice getGasPrice() {
        return new GasPrice(web3JFacade.getGasPrice());
    }

    @Override
    public EthValue getBalance(EthAddress address) {
        return EthValue.wei(web3JFacade.getBalance(address).getBalance());
    }

    @Override
    public boolean addressExists(EthAddress address) {
        return web3JFacade.getTransactionCount(address).intValue() > 0 || web3JFacade.getBalance(address).getBalance().intValue() > 0 || !web3JFacade.getCode(address).isEmpty();
    }

    @Override
    public EthHash submit(EthAccount account, EthAddress address, EthValue value, EthData data, Nonce nonce, GasUsage gasLimit) {
        RawTransaction tx = web3JFacade.createTransaction(nonce, getGasPrice(), gasLimit, address, value, data);
        EthData signedMessage = EthData.of(TransactionEncoder.signMessage(tx, (byte)chainId.id, Credentials.create(Numeric.toHexStringNoPrefix(account.getBigIntPrivateKey()))));
        web3JFacade.sendTransaction(signedMessage);

        return EthHash.of(Crypto.sha3(signedMessage).data);
    }

    @Override
    public GasUsage estimateGas(EthAccount account, EthAddress address, EthValue value, EthData data) {
        return new GasUsage(web3JFacade.estimateGas(account, address, value, data));
    }

    @Override
    public Nonce getNonce(EthAddress currentAddress) {
        return new Nonce(web3JFacade.getTransactionCount(currentAddress));
    }

    @Override
    public long getCurrentBlockNumber() {
        return web3JFacade.getCurrentBlockNumber();
    }

    @Override
    public BlockInfo getBlock(long number) {
        return toBlockInfo(web3JFacade.getBlock(number));
    }

    @Override
    public BlockInfo getBlock(EthHash ethHash) {
        return toBlockInfo(web3JFacade.getBlock(ethHash));
    }

    @Override
    public SmartContractByteCode getCode(EthAddress address) {
        return web3JFacade.getCode(address);
    }

    @Override
    public EthData constantCall(EthAccount account, EthAddress address, EthValue value, EthData data) {
        return web3JFacade.constantCall(account, address, data);
    }

    @Override
    public void register(EthereumEventHandler eventHandler) {
        ethereumRpcEventGenerator.addListener(eventHandler);
    }

    BlockInfo toBlockInfo(EthBlock ethBlock) {
        EthBlock.Block block = ethBlock.getBlock();

        Map<String, EthBlock.TransactionObject> txObjects = block.getTransactions().stream()
                .map(tx -> (EthBlock.TransactionObject)tx.get()).collect(Collectors.toMap(EthBlock.TransactionObject::getHash, e -> e));

        Map<String, org.web3j.protocol.core.methods.response.TransactionReceipt> receipts = txObjects.values().stream()
                .map(tx -> web3JFacade.getReceipt(EthHash.of(tx.getHash())))
                .collect(Collectors.toMap(org.web3j.protocol.core.methods.response.TransactionReceipt::getTransactionHash, e -> e));

        List<TransactionReceipt> receiptList = receipts.entrySet().stream()
                .map(entry -> toReceipt(txObjects.get(entry.getKey()), entry.getValue())).collect(Collectors.toList());

        return new BlockInfo(block.getNumber().longValue(), receiptList);
    }

    private TransactionReceipt toReceipt(EthBlock.TransactionObject txObject, org.web3j.protocol.core.methods.response.TransactionReceipt tx) {
        boolean successful = !tx.getGasUsed().equals(txObject.getGas());
        String error = "";
        if(!successful) {
            error = "All the gas was used! an error occurred here";
        }

        return new TransactionReceipt(EthHash.of(tx.getTransactionHash()), EthAddress.of(tx.getFrom()),EthAddress.of(tx.getTo()), EthAddress.of(tx.getContractAddress()), error, EthData.empty(), successful, toEventInfos(tx.getLogs()));
    }

    private List<EventInfo> toEventInfos(List<Log> logs) {
        return logs.stream().map(this::toEventInfo).collect(Collectors.toList());
    }

    private EventInfo toEventInfo(Log log) {
        List<EthData> topics = log.getTopics().stream().map(EthData::of).collect(Collectors.toList());
        EthData eventSignature = topics.get(0);
        EthData eventArguments = EthData.of(log.getData());
        return new EventInfo(eventSignature, eventArguments, topics.subList(1, topics.size()));
    }
}
