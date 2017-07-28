package org.adridadou.ethereum.rpc;

import org.adridadou.ethereum.propeller.Crypto;
import org.adridadou.ethereum.propeller.EthereumBackend;
import org.adridadou.ethereum.propeller.event.*;
import org.adridadou.ethereum.propeller.values.*;
import org.adridadou.ethereum.propeller.values.TransactionReceipt;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.core.methods.request.RawTransaction;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    public EthHash submit(TransactionRequest request, Nonce nonce) {
        RawTransaction tx = web3JFacade.createTransaction(nonce, getGasPrice(), request.getGasLimit(), request.getAddress(), request.getValue(), request.getData());
        EthData signedMessage = EthData.of(TransactionEncoder.signMessage(tx, (byte)chainId.id, Credentials.create(Numeric.toHexStringNoPrefix(request.getAccount().getBigIntPrivateKey()))));
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

    @Override
    public Optional<TransactionInfo> getTransactionInfo(EthHash hash) {
        return Optional.ofNullable(web3JFacade.getReceipt(hash)).flatMap(web3jReceipt -> Optional.ofNullable(web3JFacade.getTransaction(hash))
            .map(transaction -> {
                TransactionReceipt receipt = toReceipt(transaction.getGas(), web3jReceipt);
                TransactionStatus status = transaction.getBlockHash().isEmpty() ? TransactionStatus.Unknown : TransactionStatus.Executed;
                return new TransactionInfo(hash, receipt, status);
            })
        );
    }

    BlockInfo toBlockInfo(EthBlock ethBlock) {
        EthBlock.Block block = ethBlock.getBlock();

        Map<String, EthBlock.TransactionObject> txObjects = block.getTransactions().stream()
                .map(tx -> (EthBlock.TransactionObject)tx.get()).collect(Collectors.toMap(EthBlock.TransactionObject::getHash, e -> e));

        Map<String, org.web3j.protocol.core.methods.response.TransactionReceipt> receipts = txObjects.values().stream()
                .map(tx -> web3JFacade.getReceipt(EthHash.of(tx.getHash())))
                .collect(Collectors.toMap(org.web3j.protocol.core.methods.response.TransactionReceipt::getTransactionHash, e -> e));

        List<TransactionReceipt> receiptList = receipts.entrySet().stream()
                .map(entry -> toReceipt(txObjects.get(entry.getKey()).getGas(), entry.getValue())).collect(Collectors.toList());

        return new BlockInfo(block.getNumber().longValue(), receiptList);
    }

    private TransactionReceipt toReceipt(BigInteger gasLimit, org.web3j.protocol.core.methods.response.TransactionReceipt tx) {
        boolean successful = !tx.getGasUsed().equals(gasLimit);
        String error = "";
        if(!successful) {
            error = "All the gas was used! an error occurred";
        }

        return new TransactionReceipt(EthHash.of(tx.getTransactionHash()), EthHash.of(tx.getBlockHash()), EthAddress.of(tx.getFrom()),EthAddress.of(tx.getTo()), EthAddress.of(tx.getContractAddress()), error, EthData.empty(), successful, toEventInfos(EthHash.of(tx.getTransactionHash()), tx.getLogs()));
    }

    private List<EventData> toEventInfos(EthHash transactionHash, List<Log> logs) {
        return logs.stream().map(log -> this.toEventInfo(transactionHash, log)).collect(Collectors.toList());
    }

    private EventData toEventInfo(EthHash transactionHash, Log log) {
        List<EthData> topics = log.getTopics().stream().map(EthData::of).collect(Collectors.toList());
        EthData eventSignature = topics.get(0);
        EthData eventArguments = EthData.of(log.getData());
        return new EventData(transactionHash, eventSignature, eventArguments, topics.subList(1, topics.size()));
    }
}
