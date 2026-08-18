package com.nudgecraft.mixin;

import com.nudgecraft.event.BlockPlaceEventHandler;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin para interceptar a colocação de qualquer tipo de bloco efetuada por um jogador
 * e delegar a resposta estética e de partículas douradas para o tratador de eventos correspondente.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

    /**
     * Interceta a conclusão do método {@link BlockItem#place(BlockPlaceContext)}
     * e notifica o gestor de eventos de colocação sobre o resultado obtido.
     *
     * @param context O contexto físico do item de bloco que foi colocado.
     * @param cir     O objeto de retorno com o resultado da interação.
     */
    @Inject(method = "place", at = @At("RETURN"))
    private void onPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        BlockPlaceEventHandler.onBlockPlaced(context, cir.getReturnValue());
    }
}
