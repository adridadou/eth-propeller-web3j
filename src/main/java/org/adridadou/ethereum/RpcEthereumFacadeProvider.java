package org.adridadou.ethereum;

import org.adridadou.ethereum.propeller.CoreEthereumFacadeProvider;
import org.adridadou.ethereum.propeller.EthereumFacade;
import org.adridadou.ethereum.propeller.event.EthereumEventHandler;
import org.adridadou.ethereum.propeller.values.ChainId;
import org.adridadou.ethereum.rpc.EthereumRpc;
import org.adridadou.ethereum.rpc.EthereumRpcEventGenerator;
import org.adridadou.ethereum.rpc.Web3JFacade;
import org.adridadou.ethereum.values.config.InfuraKey;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;


/**
 * Created by davidroon on 27.04.16.
 * This code is released under Apache 2 license
 */
public class RpcEthereumFacadeProvider {
    public static final ChainId MAIN_CHAIN_ID = ChainId.id(0);
    public static final ChainId ROPSTEN_CHAIN_ID = ChainId.id(3);
    public static final ChainId ETHER_CAMP_CHAIN_ID = ChainId.id(161);
    public static final ChainId KOVAN_CHAIN_ID = ChainId.id(42);

    private RpcEthereumFacadeProvider() {}

    public static EthereumFacade forRemoteNode(final String url, final ChainId chainId) {
        Web3JFacade web3j = new Web3JFacade(Web3j.build(new HttpService(url)));
        EthereumRpc ethRpc = new EthereumRpc(web3j, new EthereumRpcEventGenerator(web3j), chainId);
        EthereumEventHandler eventHandler = new EthereumEventHandler();
        eventHandler.onReady();
        return CoreEthereumFacadeProvider.create(ethRpc, eventHandler);
    }

    public static InfuraBuilder forInfura(final InfuraKey key)  {
        return new InfuraBuilder(key);
    }

    public static class InfuraBuilder {
        private final InfuraKey key;

        public InfuraBuilder(InfuraKey key) {
            this.key = key;
        }

        public EthereumFacade createMain() {
            return forRemoteNode("https://main.infura.io/" + key.key, RpcEthereumFacadeProvider.MAIN_CHAIN_ID);
        }

        public EthereumFacade createRopsten() {
            return forRemoteNode("https://ropsten.infura.io/" + key.key, RpcEthereumFacadeProvider.ROPSTEN_CHAIN_ID);
        }

        public EthereumFacade createKovan() {
            return forRemoteNode("https://kovan.infura.io/" + key.key, RpcEthereumFacadeProvider.KOVAN_CHAIN_ID);
        }
    }
}
