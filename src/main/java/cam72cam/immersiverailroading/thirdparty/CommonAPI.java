package cam72cam.immersiverailroading.thirdparty;

import cam72cam.immersiverailroading.entity.*;
import cam72cam.immersiverailroading.physics.PhysicsAccummulator;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.registry.LocomotiveDefinition;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.fluid.Fluid;
import cam72cam.mod.math.Vec3i;
import cam72cam.immersiverailroading.thirdparty.event.TagEvent;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.*;

public class CommonAPI {
    private final EntityRollingStock stock;

    public static CommonAPI create(World world, BlockPos pos) {
        return create(world, pos, EntityRollingStock.class);
    }

    public static CommonAPI create(World world, BlockPos pos, Class<? extends EntityRollingStock> stockClass) {
        TileRailBase te = cam72cam.mod.world.World.get(world).getBlockEntity(new Vec3i(pos), TileRailBase.class);
        if (te != null) {
            EntityRollingStock stock = te.getStockNearBy(stockClass);
            if (stock != null) {
                return new CommonAPI(stock);
            }
        }
        return null;
    }

    public CommonAPI(EntityRollingStock stock) {
        this.stock = stock;
    }

    public FluidStack getFluid() {
//        Capability<ITank> energyCapability = CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY;
//        ITank fh = stock.getCapability(energyCapability, null);
//        if (fh != null) {
//            return fh.drain(Integer.MAX_VALUE, false);
//        }
        if (stock instanceof FreightTank) {
            return ((FreightTank) stock).theTank.getContents().internal;
        }
        return null;
    }

    public Map<String, Object> info() {
        if (stock != null) {
            Map<String, Object> info = new HashMap<>();
            EntityRollingStockDefinition def = stock.getDefinition();

            info.put("id", def.defID);
            info.put("name", def.name());
            info.put("tag", stock.tag);
            info.put("weight", stock.getWeight());

            EnumFacing dir = EnumFacing.fromAngle(stock.getRotationYaw());
            if (stock instanceof EntityMoveableRollingStock) {
                EntityMoveableRollingStock movable = (EntityMoveableRollingStock) stock;
                info.put("speed", movable.getCurrentSpeed().metric());

                if (movable.getCurrentSpeed().metric() < 0) {
                    dir = dir.getOpposite();
                }
            }
            info.put("direction", dir.toString());

            if (stock instanceof EntityRidableRollingStock) {
                info.put("passengers", stock.getPassengerCount());
            }

            if (stock instanceof Locomotive) {
                Locomotive loco = (Locomotive) stock;
                LocomotiveDefinition locoDef = loco.getDefinition();
                info.put("horsepower", locoDef.getHorsePower(loco.gauge));
                info.put("traction", locoDef.getStartingTractionNewtons(loco.gauge));
                info.put("max_speed", locoDef.getMaxSpeed(loco.gauge).metric());
                info.put("brake", loco.getAirBrake());
                info.put("throttle", loco.getThrottle());

                if (loco instanceof LocomotiveSteam) {
                    LocomotiveSteam steam = (LocomotiveSteam) loco;
                    info.put("pressure", steam.getBoilerPressure());
                    info.put("temperature", steam.getBoilerTemperature());
                }
                if (loco instanceof LocomotiveDiesel) {
                    info.put("temperature", ((LocomotiveDiesel) loco).getEngineTemperature());
                }
            }

//            FluidStack fluid = getFluid();
//            if (fluid != null) {
//                info.put("fluid_type", fluid.getFluid().getName());
//                info.put("fluid_amount", fluid.amount);
//            } else {
//                info.put("fluid_type", null);
//                info.put("fluid_amount", 0);
//            }
            if (stock instanceof FreightTank) {
                FreightTank tanker = (FreightTank) stock;
                Fluid fluid = tanker.getLiquid();
                info.put("fluid_type", fluid != null ? tanker.getLiquid().ident : null);
                info.put("fluid_amount", tanker.getLiquidAmount());
                info.put("fluid_max", tanker.getTankCapacity().MilliBuckets());
            }

            if (stock instanceof Freight) {
                Freight freight = ((Freight) stock);
                info.put("cargo_percent", freight.getPercentCargoFull());
                info.put("cargo_size", freight.getInventorySize());
            }
            return info;
        }
        return null;
    }

    public Map<String, Object> consist(boolean supportsList) {
        if (!(stock instanceof EntityCoupleableRollingStock)) {
            return null;
        }
        EntityCoupleableRollingStock stock = (EntityCoupleableRollingStock) this.stock;

        int traction = 0;
        PhysicsAccummulator acc = new PhysicsAccummulator(stock.getCurrentTickPosAndPrune());
        stock.mapTrain(stock, true, true, acc::accumulate);
        Map<String, Object> info = new HashMap<>();
        List<Object> locos = new ArrayList<>();
        List<Object> cars = new ArrayList<>();

        info.put("cars", acc.count);
        info.put("tractive_effort_N", acc.tractiveEffortNewtons);
        info.put("weight_kg", acc.massToMoveKg);
        info.put("speed_km", stock.getCurrentSpeed().metric());
        EnumFacing dir = EnumFacing.fromAngle(stock.getRotationYaw());
        if (stock.getCurrentSpeed().metric() < 0) {
            dir = dir.getOpposite();
        }
        info.put("direction", dir.toString());

        for (EntityCoupleableRollingStock car : stock.getTrain()) {
            if (car instanceof Locomotive) {
                LocomotiveDefinition locoDef = ((Locomotive) car).getDefinition();
                traction += locoDef.getStartingTractionNewtons(car.gauge);
                locos.add(new CommonAPI(car).info());
            } else {
                cars.add(new CommonAPI(car).info());
            }
        }
        if (supportsList) {
            info.put("locomotives", locos);
            info.put("cars", cars);
        } else {
            Map<String, Object> locomotives = new HashMap<>();
            for (int i = 0; i < locos.size(); i++) {
                locomotives.put("" + i, locos.get(i));
            }
            info.put("locomotives", locomotives);

            Map<String, Object> trainCars = new HashMap<>();
            for (int i = 0; i < cars.size(); i++) {
                trainCars.put("" + i, cars.get(i));
            }
            info.put("cars", trainCars);
        }
        info.put("total_traction_N", traction);
        return info;
    }

    public String getTag() {
    	TagEvent.GetTagEvent tagEvent = new TagEvent.GetTagEvent(stock.getUUID());
    	MinecraftForge.EVENT_BUS.post(tagEvent);
    	
    	if (tagEvent.tag != null)
    	{
    		return tagEvent.tag;
    	}
    	
        return stock.tag;
    }

    public void setTag(String tag) {
    	TagEvent.SetTagEvent tagEvent = new TagEvent.SetTagEvent(stock.getUUID(), tag);
    	MinecraftForge.EVENT_BUS.post(tagEvent);
    	
        stock.tag = tag;
    }

    private float normalize(double val) {
        if (Double.isNaN(val)) {
            return 0;
        }
        if (val > 1) {
            return 1;
        }
        if (val < -1) {
            return -1;
        }
        return (float)val;
    }

    public void setThrottle(double throttle) {
        if (stock instanceof Locomotive) {
            ((Locomotive)stock).setThrottle(normalize(throttle));
        }
    }
    public void setAirBrake(double brake) {
        if (stock instanceof Locomotive) {
            ((Locomotive)stock).setAirBrake(normalize(brake));
        }
    }

    public void setHorn(int horn) {
        if (stock instanceof Locomotive) {
            ((Locomotive)stock).setHorn(horn, null);
        }
    }

    public void setBell(int bell) {
        if (stock instanceof Locomotive) {
            ((Locomotive)stock).setBell(bell);
        }
    }

    public Vec3d getPosition() {
        return stock.getPosition().internal();
    }

    public UUID getUniqueID() {
        return stock.getUUID();
    }
}
